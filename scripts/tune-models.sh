#!/usr/bin/env bash
# ============================================================
# tune-models.sh — Run hyperparameter tuning against models
# already registered in ai_model_registry.
#
# This is an optimisation pass — it re-promotes models with
# better hyperparameters found via k-fold cross-validation.
# Models remain ACTIVE throughout; new versions are promoted
# only when CV metrics improve.
#
# Run periodically as data accumulates, after adding new models,
# or after updating the hyperparameter grid.
#
# Prerequisites:
#   - seed-train.sh has been run at least once
#   - Backend running:  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
#   - jq installed:     brew install jq
#
# Usage:
#   chmod +x scripts/tune-models.sh
#   ./scripts/tune-models.sh [BASE_URL] [JWT_TOKEN] [DISTRIBUTOR_ID] [MODELS]
#
# Arguments:
#   BASE_URL        Backend base URL (default: http://localhost:8080/api)
#   JWT_TOKEN       Bearer token; if omitted, logs in as superadmin
#   DISTRIBUTOR_ID  Distributor UUID (default: a0eebc99-...)
#   MODELS          Comma-separated model names to tune (default: all 15)
#
# Examples:
#   # Tune all 15 models
#   ./scripts/tune-models.sh
#
#   # Tune a single model after updating its feature builder
#   ./scripts/tune-models.sh "" "" "" "churn_predictor"
#
#   # Tune two Phase 7 models after adding training data
#   ./scripts/tune-models.sh "" "" "" "cash_flow_predictor,customer_clv_predictor"
#
# Available model names:
#   credit_classifier         stockout_predictor        rep_performance_predictor
#   payment_distress_classifier  data_quality_detector  credit_limit_regressor
#   demand_forecaster         shrinkage_detector        payment_anomaly_detector
#   bank_recon_matcher        churn_predictor           expiry_risk_predictor
#   cash_flow_predictor       customer_clv_predictor    customer_segmenter
# ============================================================

set -euo pipefail

BASE_URL="${1:-http://localhost:8080/api}"
TOKEN="${2:-}"
DISTRIBUTOR_ID="${3:-a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11}"
MODELS="${4:-}"   # comma-separated; empty = all

HEALTH_ENDPOINT="${BASE_URL}/actuator/health"
TUNE_ENDPOINT="${BASE_URL}/v1/ai/admin/tune/${DISTRIBUTOR_ID}"

# Append ?models= query param when a filter is specified
if [[ -n "${MODELS}" ]]; then
    # URL-encode commas → %2C is NOT needed; Spring accepts comma-separated list params
    TUNE_ENDPOINT_WITH_FILTER="${TUNE_ENDPOINT}?models=$(echo "${MODELS}" | sed 's/,/\&models=/g')"
else
    TUNE_ENDPOINT_WITH_FILTER="${TUNE_ENDPOINT}"
fi

# ── Helpers ──────────────────────────────────────────────────────────────

log()  { echo "[tune-models] $*" >&2; }
warn() { echo "[tune-models] WARN: $*" >&2; }
die()  { echo "[tune-models] ERROR: $*" >&2; exit 1; }

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

# ── Trigger tuning ─────────────────────────────────────────────────────────

trigger_tuning() {
    local jwt="$1"

    if [[ -n "${MODELS}" ]]; then
        log "Triggering tuning for models: ${MODELS}"
    else
        log "Triggering tuning for all 15 models..."
    fi
    log "  Endpoint: ${TUNE_ENDPOINT_WITH_FILTER}"

    local response http_code body lines

    response=$(curl -s \
        -X POST \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer ${jwt}" \
        -w "\n%{http_code}" \
        "${TUNE_ENDPOINT_WITH_FILTER}" 2>&1) || true

    http_code=$(echo "${response}" | tail -n1)
    lines=$(echo "${response}" | wc -l | tr -d ' ')
    body=$(echo "${response}" | head -n $((lines - 1)))

    if [[ "${http_code}" =~ ^2 ]]; then
        log "Tuning triggered (HTTP ${http_code})."
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
        warn "Unexpected response (HTTP ${http_code}):"
        echo "${body}"
        die "Tuning request failed. Check backend logs."
    fi
}

# ── Poll tuning status ─────────────────────────────────────────────────────

poll_tuning_status() {
    local jwt="$1"
    local job_id="$2"
    local status_url="${BASE_URL}/v1/ai/admin/tune/${job_id}/status"

    # Tuning all 15 models takes ~10–20 min (k-fold CV per model).
    # 90 polls × 20s = 30 min.
    log "Polling tuning status (every 20s, max 30 min)..."
    local attempts=0 unknown_streak=0
    while true; do
        attempts=$((attempts + 1))
        if [[ ${attempts} -gt 90 ]]; then
            warn "Timed out after 30 min. Tuning may still be running in the background."
            warn "Query DB: SELECT model_type,status,updated_at FROM ai_model_registry ORDER BY updated_at DESC LIMIT 20;"
            exit 1
        fi

        local status_response status
        status_response=$(curl -sf "${status_url}" -H "Authorization: Bearer ${jwt}" 2>/dev/null || echo '{}')

        if command -v jq &>/dev/null; then
            status=$(echo "${status_response}" | jq -r '.data.status // "UNKNOWN"' 2>/dev/null || echo "UNKNOWN")
        else
            status=$(echo "${status_response}" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('status','UNKNOWN'))" 2>/dev/null || echo "UNKNOWN")
        fi

        log "  Job ${job_id}: ${status} (poll ${attempts}/90)"

        if [[ "${status}" == "COMPLETED" || "${status}" == "COMPLETED_WITH_ERRORS" ]]; then
            log "Tuning finished: ${status}."
            if [[ "${status}" == "COMPLETED_WITH_ERRORS" ]]; then
                warn "Some models failed to tune — check logs. Successfully tuned models remain ACTIVE."
            fi
            break
        elif [[ "${status}" == "FAILED" ]]; then
            warn "Tuning job FAILED. Check backend logs."
            exit 1
        elif [[ "${status}" == "UNKNOWN" ]]; then
            unknown_streak=$((unknown_streak + 1))
            warn "  UNKNOWN response (streak=${unknown_streak}/10) — backend may be under GC pressure, retrying..."
            if [[ ${unknown_streak} -ge 10 ]]; then
                warn "10 consecutive UNKNOWN responses. Backend may have crashed."
                warn "Query DB: SELECT model_type,status,updated_at FROM ai_model_registry ORDER BY updated_at DESC LIMIT 20;"
                exit 1
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
    log "  Zuqi Hyperparameter Tuning Script"
    log "  Target:      ${BASE_URL}"
    log "  Distributor: ${DISTRIBUTOR_ID}"
    if [[ -n "${MODELS}" ]]; then
        log "  Models:      ${MODELS}"
    else
        log "  Models:      all 15"
    fi
    log "  Purpose:     Optimise hyperparameters via k-fold CV."
    log "               Re-run periodically as data accumulates."
    log "======================================================"

    wait_for_backend
    local jwt
    jwt=$(get_token)
    trigger_tuning "${jwt}"

    log ""
    log "Tuned models are promoted to ACTIVE with best hyperparameters."
    log "======================================================"
}

main "$@"
