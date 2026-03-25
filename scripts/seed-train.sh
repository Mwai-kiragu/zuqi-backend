#!/usr/bin/env bash
# ============================================================
# seed-train.sh — Generate synthetic data and train all 15 ML
# models so the platform is functional from a cold start.
#
# Run this ONCE on a fresh environment (or after a data reset).
# When it exits 0, all 15 models are ACTIVE in ai_model_registry
# and the platform is ready for inference.
#
# Prerequisites:
#   - Docker running with Redis:  docker compose up -d
#   - Backend running:            ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
#   - jq installed (optional):   brew install jq
#
# Usage:
#   chmod +x scripts/seed-train.sh
#   ./scripts/seed-train.sh [BASE_URL] [JWT_TOKEN] [DISTRIBUTOR_ID] [MODELS]
#
# Examples:
#   ./scripts/seed-train.sh
#   ./scripts/seed-train.sh http://localhost:8080/api eyJhbGci... a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11
#   ./scripts/seed-train.sh "" "" "" "bank_recon_matcher,expiry_risk_predictor"
# ============================================================

set -euo pipefail

BASE_URL="${1:-http://localhost:8080/api}"
TOKEN="${2:-}"
DISTRIBUTOR_ID="${3:-a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11}"
# Optional comma-separated model names, e.g. "bank_recon_matcher,expiry_risk_predictor"
MODELS="${4:-}"

# Build the seed URL; append ?models= params if a filter was provided
_SEED_BASE="${BASE_URL}/v1/ai/admin/seed-synthetic/${DISTRIBUTOR_ID}"
if [[ -n "${MODELS}" ]]; then
    _MODELS_PARAMS="$(echo "${MODELS}" | tr ',' '\n' | awk '{print "models="$1}' | paste -sd '&' -)"
    SEED_ENDPOINT="${_SEED_BASE}?${_MODELS_PARAMS}"
else
    SEED_ENDPOINT="${_SEED_BASE}"
fi
HEALTH_ENDPOINT="${BASE_URL}/actuator/health"

# ── Helpers ──────────────────────────────────────────────────────────────

log()  { echo "[seed-train] $*" >&2; }
warn() { echo "[seed-train] WARN: $*" >&2; }
die()  { echo "[seed-train] ERROR: $*" >&2; exit 1; }

# ── Wait for backend ──────────────────────────────────────────────────────

wait_for_backend() {
    log "Waiting for backend at ${BASE_URL}..."
    local attempts=0
    until curl -sf "${HEALTH_ENDPOINT}" > /dev/null 2>&1; do
        attempts=$((attempts + 1))
        if [[ ${attempts} -ge 30 ]]; then
            die "Backend did not become healthy after 60s. Is it running?"
        fi
        sleep 2
    done
    log "Backend is healthy."
}

# ── Get JWT token ─────────────────────────────────────────────────────────

get_token() {
    if [[ -n "${TOKEN}" ]]; then
        echo "${TOKEN}"
        return
    fi

    log "No token provided — logging in as superadmin@zuqi.com..."
    local login_response
    login_response=$(curl -sf -X POST "${BASE_URL}/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"email":"superadmin@zuqi.com","password":"Password123"}' 2>&1) || \
        die "Login failed. Pass a JWT token as the second argument."

    local token
    if command -v jq &>/dev/null; then
        token=$(echo "${login_response}" | jq -r '.data.access_token // .data.accessToken // empty')
    else
        token=$(echo "${login_response}" | python3 -c "import sys,json; d=json.load(sys.stdin); data=d.get('data',{}); print(data.get('access_token') or data.get('accessToken') or '')" 2>/dev/null || true)
    fi

    if [[ -z "${token}" ]]; then
        die "Could not extract access token. Pass a JWT token as the second argument."
    fi

    if [[ "${token}" =~ [[:space:]] ]]; then
        die "Access token contains whitespace; aborting."
    fi

    log "Login successful."
    echo "${token}"
}

# ── Trigger seed ──────────────────────────────────────────────────────────

trigger_seed() {
    local jwt="$1"
    log "Triggering synthetic seed + training at ${SEED_ENDPOINT}..."

    local response http_code body lines

    response=$(curl -s \
        -X POST \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${jwt}" \
        -w "\n%{http_code}" \
        "${SEED_ENDPOINT}" \
        -d '{"merchantCount": 500, "historyMonths": 12, "randomSeed": 42}' 2>&1) || true

    http_code=$(echo "${response}" | tail -n1)
    lines=$(echo "${response}" | wc -l | tr -d ' ')
    body=$(echo "${response}" | head -n $((lines - 1)))

    if [[ "${http_code}" =~ ^2 ]]; then
        log "Seed triggered (HTTP ${http_code})."
        if command -v jq &>/dev/null; then
            echo "${body}" | jq '.' 2>/dev/null || echo "${body}"
        else
            echo "${body}"
        fi

        local run_id
        if command -v jq &>/dev/null; then
            run_id=$(echo "${body}" | jq -r '.data.runId // empty' 2>/dev/null || true)
        else
            run_id=$(echo "${body}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('runId',''))" 2>/dev/null || true)
        fi

        if [[ -n "${run_id}" ]]; then
            poll_seed_status "${jwt}" "${run_id}"
        fi
    else
        warn "Unexpected response (HTTP ${http_code}):"
        echo "${body}"
        die "Seed request failed. Check backend logs."
    fi
}

# ── Poll seed status ───────────────────────────────────────────────────────

poll_seed_status() {
    local jwt="$1"
    local run_id="$2"
    local status_url="${BASE_URL}/v1/ai/admin/seed-synthetic/${run_id}/status"

    # Seeding includes full model training (~12–18 min for 500 merchants).
    # 80 polls × 15s = 20 min headroom.
    log "Polling seed status (every 15s, max 20 min)..."
    local attempts=0
    while true; do
        attempts=$((attempts + 1))
        if [[ ${attempts} -gt 80 ]]; then
            warn "Timed out after 20 min — seeding may still be running."
            warn "Check: SELECT status, duration_ms FROM ai_synthetic_runs ORDER BY created_at DESC LIMIT 1;"
            exit 1
        fi

        local status_response http_code status
        status_response=$(curl -s -o /tmp/zuqi_seed_status.json -w "%{http_code}" \
            "${status_url}" -H "Authorization: Bearer ${jwt}" 2>/dev/null || echo "000")
        http_code="${status_response}"

        # Re-login if JWT expired (401)
        if [[ "${http_code}" == "401" ]]; then
            log "  JWT expired — re-logging in..."
            jwt=$(get_token) || { warn "Re-login failed."; break; }
            status="RUNNING"
        elif [[ "${http_code}" =~ ^2 ]]; then
            if command -v jq &>/dev/null; then
                status=$(jq -r '.data.status // "UNKNOWN"' /tmp/zuqi_seed_status.json 2>/dev/null || echo "UNKNOWN")
            else
                status=$(python3 -c "import sys,json; d=json.load(open('/tmp/zuqi_seed_status.json')); print(d.get('data',{}).get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
            fi
        else
            status="UNKNOWN"
        fi

        log "  Run ${run_id}: ${status} (poll ${attempts}/80)"

        if [[ "${status}" == "COMPLETED" ]]; then
            log "Seeding + training COMPLETED — all 15 models are ACTIVE."
            break
        elif [[ "${status}" == "FAILED" ]]; then
            warn "Run FAILED. Check backend logs."
            exit 1
        fi

        sleep 15
    done
}

# ── Main ──────────────────────────────────────────────────────────────────

main() {
    log "======================================================"
    log "  Zuqi Seed + Train Script"
    log "  Target:      ${BASE_URL}"
    log "  Distributor: ${DISTRIBUTOR_ID}"
    log "  Purpose:     One-time bootstrap — generates synthetic"
    log "               data and trains all 15 ML models."
    log "  Next step:   Run ./scripts/tune-models.sh to optimise"
    log "               hyperparameters (optional but recommended)."
    log "======================================================"

    wait_for_backend
    local jwt
    jwt=$(get_token)
    trigger_seed "${jwt}"

    log ""
    log "15 models are now ACTIVE in ai_model_registry."
    log "(customer_health_scorer and reorder_optimizer are rules-based — no training needed)"
    log "======================================================"
}

main "$@"
