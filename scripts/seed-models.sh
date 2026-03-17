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

# ── Helpers ──────────────────────────────────────────────────────────────

log()  { echo "[seed-models] $*"; }
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
        token=$(echo "${login_response}" | jq -r '.data.access_token // empty')
    else
        token=$(echo "${login_response}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('access_token',''))" 2>/dev/null || true)
    fi

    if [[ -z "${token}" ]]; then
        die "Could not extract access token from login response. Pass a JWT token as the second argument."
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
        -d '{"merchantCount": 500, "historyMonths": 12, "seed": 42}' 2>&1) || true

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

poll_status() {
    local jwt="$1"
    local run_id="$2"
    local status_url="${BASE_URL}/v1/ai/admin/seed-synthetic/${run_id}/status"

    log "Polling run status (every 15s)..."
    local attempts=0
    while true; do
        attempts=$((attempts + 1))
        if [[ ${attempts} -gt 40 ]]; then
            warn "Timed out waiting for run to complete. Check backend logs."
            break
        fi

        local status_response status
        status_response=$(curl -sf "${status_url}" -H "Authorization: Bearer ${jwt}" 2>/dev/null || echo '{}')

        if command -v jq &>/dev/null; then
            status=$(echo "${status_response}" | jq -r '.data.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
        else
            status=$(echo "${status_response}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
        fi

        log "  Run ${run_id}: ${status}"

        if [[ "${status}" == "COMPLETED" ]]; then
            log "Generation and model training COMPLETED."
            break
        elif [[ "${status}" == "FAILED" ]]; then
            warn "Run FAILED. Check backend logs for details."
            break
        fi

        sleep 15
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

    log ""
    log "When training completes, all 9 models will be ACTIVE"
    log "in the ai_model_registry table and ready for inference."
    log "======================================================"
}

main "$@"
