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
#   ./scripts/seed-models.sh [BASE_URL] [JWT_TOKEN]
#
# Examples:
#   ./scripts/seed-models.sh
#   ./scripts/seed-models.sh http://localhost:8080/api eyJhbGci...
# ============================================================

set -euo pipefail

BASE_URL="${1:-http://localhost:8080/api}"
TOKEN="${2:-}"

SEED_ENDPOINT="${BASE_URL}/v1/ai/synthetic/seed"
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

# ── Trigger seed ──────────────────────────────────────────────────────────

trigger_seed() {
    log "Triggering synthetic seed at ${SEED_ENDPOINT}..."

    local http_code
    local response

    response=$(curl -sf \
        -X POST \
        -H "Content-Type: application/json" \
        -H "$(auth_header)" \
        -w "\n%{http_code}" \
        "${SEED_ENDPOINT}" \
        -d '{
            "merchantCount": 500,
            "weeksOfHistory": 52,
            "seed": 42
        }' 2>&1) || true

    http_code=$(echo "${response}" | tail -n1)
    body=$(echo "${response}" | head -n-1)

    if [[ "${http_code}" =~ ^2 ]]; then
        log "Seed triggered successfully (HTTP ${http_code})."
        if command -v jq &>/dev/null; then
            echo "${body}" | jq '.' 2>/dev/null || echo "${body}"
        else
            echo "${body}"
        fi
    else
        warn "Unexpected response (HTTP ${http_code}):"
        echo "${body}"
        warn "The seed request may have failed. Check backend logs."
        warn "  ./mvnw spring-boot:run ... | grep -i 'synthetic\|training'"
    fi
}

# ── Main ──────────────────────────────────────────────────────────────────

main() {
    log "======================================================"
    log "  Zuqi Model Seeding Script"
    log "  Target: ${BASE_URL}"
    log "======================================================"

    wait_for_backend
    trigger_seed

    log ""
    log "Model training runs asynchronously in the background."
    log "Monitor progress:"
    log "  curl ${BASE_URL}/v1/ai/synthetic/status"
    log ""
    log "When training completes, all 9 models will be ACTIVE"
    log "in the ai_model_registry table and ready for inference."
    log "======================================================"
}

main "$@"
