#!/usr/bin/env bash
# ============================================================
# seed-models.sh — Trigger synthetic data generation and
# initial ML model training on a running Zuqi backend.
#
# Prerequisites:
#   - Docker running with Redis:  docker compose up -d
#   - Backend running:            ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
#   - jq installed (optional):   brew install jq
#
# Usage:
#   chmod +x scripts/seed-models.sh
#   ./scripts/seed-models.sh [BASE_URL] [JWT_TOKEN] [DISTRIBUTOR_ID]
#
# Examples:
#   ./scripts/seed-models.sh
#   ./scripts/seed-models.sh http://localhost:8080/api eyJhbGci... a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11
# ============================================================

set -euo pipefail

BASE_URL="${1:-http://localhost:8080/api}"
TOKEN="${2:-}"
DISTRIBUTOR_ID="${3:-a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11}"

SEED_ENDPOINT="${BASE_URL}/v1/ai/admin/seed-synthetic/${DISTRIBUTOR_ID}"
HEALTH_ENDPOINT="${BASE_URL}/actuator/health"
TUNE_ENDPOINT="${BASE_URL}/v1/ai/admin/tune/${DISTRIBUTOR_ID}"

# ── Helpers ──────────────────────────────────────────────────────────────

log()  { echo "[seed-models] $*" >&2; }
warn() { echo "[seed-models] WARN: $*" >&2; }
die()  { echo "[seed-models] ERROR: $*" >&2; exit 1; }

auth_header() {
    if [[ -n "${TOKEN}" ]]; then
        echo "Authorization: Bearer ${TOKEN}"
    else
        echo "X-No-Auth: true"
    fi
}

# ── Wait for backend ──────────────────────────────────────────────────────

wait_for_backend() {
    log "Waiting for backend at ${BASE_URL}..."
    local attempts=0
    until curl -sf "${HEALTH_ENDPOINT}" > /dev/null 2>&1; do
        attempts=$((attempts + 1))
        if [[ ${attempts} -ge 30 ]]; then
            die "Backend did not become healthy after 60 s. Is it running?"
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
        die "Could not extract access token from login response. Pass a JWT token as the second argument."
    fi

    # Basic sanity check to avoid sending log lines as headers
    if [[ "${token}" =~ [[:space:]] ]]; then
        die "Access token contains whitespace; aborting to avoid malformed Authorization header."
    fi

    log "Login successful."
    echo "${token}"
}

# ── Trigger seed ──────────────────────────────────────────────────────────

trigger_seed() {
    local jwt="$1"
    log "Triggering synthetic seed at ${SEED_ENDPOINT}..."

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
        log "Seed triggered successfully (HTTP ${http_code})."
        if command -v jq &>/dev/null; then
            echo "${body}" | jq '.' 2>/dev/null || echo "${body}"
        else
            echo "${body}"
        fi

        # Extract run ID for status polling
        local run_id
        if command -v jq &>/dev/null; then
            run_id=$(echo "${body}" | jq -r '.data.runId // empty' 2>/dev/null || true)
        else
            run_id=$(echo "${body}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('runId',''))" 2>/dev/null || true)
        fi

        if [[ -n "${run_id}" ]]; then
            poll_status "${jwt}" "${run_id}"
        fi
    else
        warn "Unexpected response (HTTP ${http_code}):"
        echo "${body}"
        die "Seed request failed. Check backend logs: ./mvnw spring-boot:run ... | grep -i 'synthetic\|training'"
    fi
}

# ── Poll run status ───────────────────────────────────────────────────────

# Global flag: set to 1 if seed COMPLETED, 0 otherwise
SEED_COMPLETED=0

poll_status() {
    local jwt="$1"
    local run_id="$2"
    local status_url="${BASE_URL}/v1/ai/admin/seed-synthetic/${run_id}/status"

    # Seeding now includes full model training (~12–18 min for 500 merchants).
    # 80 polls × 15s = 20 min — enough headroom.
    log "Polling run status (every 15s, max 20 min)..."
    local attempts=0
    while true; do
        attempts=$((attempts + 1))
        if [[ ${attempts} -gt 80 ]]; then
            warn "Timed out waiting for run to complete after 20 min. Check backend logs."
            warn "Tuning will NOT be triggered — seeding may still be running."
            SEED_COMPLETED=0
            break
        fi

        local status_response status
        status_response=$(curl -sf "${status_url}" -H "Authorization: Bearer ${jwt}" 2>/dev/null || echo '{}')

        if command -v jq &>/dev/null; then
            status=$(echo "${status_response}" | jq -r '.data.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
        else
            status=$(echo "${status_response}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
        fi

        log "  Run ${run_id}: ${status} (poll ${attempts}/80)"

        if [[ "${status}" == "COMPLETED" ]]; then
            log "Generation and model training COMPLETED."
            SEED_COMPLETED=1
            break
        elif [[ "${status}" == "FAILED" ]]; then
            warn "Run FAILED. Check backend logs for details."
            SEED_COMPLETED=0
            break
        fi

        sleep 15
    done
}

# ── Hyperparameter tuning trigger + poll ───────────────────────────────────

trigger_tuning() {
    local jwt="$1"
    log "Triggering hyperparameter tuning at ${TUNE_ENDPOINT}..."

    local response http_code body lines

    response=$(curl -s \
        -X POST \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${jwt}" \
        -w "\n%{http_code}" \
        "${TUNE_ENDPOINT}" 2>&1) || true

    http_code=$(echo "${response}" | tail -n1)
    lines=$(echo "${response}" | wc -l | tr -d ' ')
    body=$(echo "${response}" | head -n $((lines - 1)))

    if [[ "${http_code}" =~ ^2 ]]; then
        log "Tuning triggered successfully (HTTP ${http_code})."
        if command -v jq &>/dev/null; then
            echo "${body}" | jq '.' 2>/dev/null || echo "${body}"
        else
            echo "${body}"
        fi

        local job_id
        if command -v jq &>/dev/null; then
            job_id=$(echo "${body}" | jq -r '.data.jobId // empty' 2>/dev/null || true)
        else
            job_id=$(echo "${body}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('jobId',''))" 2>/dev/null || true)
        fi

        if [[ -n "${job_id}" ]]; then
            poll_tuning_status "${jwt}" "${job_id}"
        fi
    else
        warn "Unexpected response (HTTP ${http_code}) from tuning endpoint:"
        echo "${body}"
        warn "Tuning request failed. Check backend logs."
    fi
}

poll_tuning_status() {
    local jwt="$1"
    local job_id="$2"
    local status_url="${BASE_URL}/v1/ai/admin/tune/${job_id}/status"

    log "Polling tuning status (every 20s, max 40 min)..."
    local attempts=0 unknown_streak=0
    while true; do
        attempts=$((attempts + 1))
        if [[ ${attempts} -gt 120 ]]; then
            warn "Timed out waiting for tuning to complete (40 min). Check backend logs."
            break
        fi

        local status_response status
        status_response=$(curl -sf "${status_url}" -H "Authorization: Bearer ${jwt}" 2>/dev/null || echo '{}')

        if command -v jq &>/dev/null; then
            status=$(echo "${status_response}" | jq -r '.data.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
        else
            status=$(echo "${status_response}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
        fi

        log "  Tuning job ${job_id}: ${status} (poll ${attempts}/120)"

        if [[ "${status}" == "COMPLETED" || "${status}" == "COMPLETED_WITH_ERRORS" ]]; then
            log "Hyperparameter tuning finished with status: ${status}."
            break
        elif [[ "${status}" == "FAILED" ]]; then
            warn "Tuning job FAILED. Check backend logs for details."
            break
        elif [[ "${status}" == "UNKNOWN" ]]; then
            unknown_streak=$((unknown_streak + 1))
            warn "  UNKNOWN response (streak=${unknown_streak}/10) — backend may be GC pausing under load, retrying..."
            if [[ ${unknown_streak} -ge 10 ]]; then
                warn "Status lost (10 consecutive UNKNOWN responses). Backend may have crashed."
                warn "Check backend logs. The tuning may still be running or may have completed."
                warn "Query DB: SELECT model_type,status,updated_at FROM ai_model_registry ORDER BY updated_at DESC LIMIT 20;"
                break
            fi
        else
            unknown_streak=0
        fi

        sleep 20
    done
}

# ── Main ──────────────────────────────────────────────────────────────────

main() {
    log "======================================================"
    log "  Zuqi Model Seeding Script"
    log "  Target:      ${BASE_URL}"
    log "  Distributor: ${DISTRIBUTOR_ID}"
    log "======================================================"

    wait_for_backend
    local jwt
    jwt=$(get_token)
    trigger_seed "${jwt}"

    if [[ "${SEED_COMPLETED}" -eq 1 ]]; then
        log "Seed COMPLETED — starting hyperparameter tuning..."
        trigger_tuning "${jwt}"
    else
        warn "Seed did not complete — skipping tuning to avoid overloading the JVM."
        warn "Re-run this script once seeding finishes, or trigger tuning manually:"
        warn "  curl -X POST ${TUNE_ENDPOINT} -H 'Authorization: Bearer <token>'"
    fi

    log ""
    log "When training completes, 15 models will be ACTIVE"
    log "in the ai_model_registry table and ready for inference."
    log "(customer_health_scorer and reorder_optimizer are rules-based — no training needed)"
    log "======================================================"
}

main "$@"
