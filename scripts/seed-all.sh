#!/usr/bin/env bash
# ============================================================
# seed-all.sh — Full bootstrap: seed + train then tune.
#
# Convenience wrapper for "I want everything from zero".
# Calls seed-train.sh then tune-models.sh sequentially.
#
# For day-to-day use, run them separately:
#   ./scripts/seed-train.sh   — one-time training bootstrap
#   ./scripts/tune-models.sh  — periodic hyperparameter tuning
#
# Usage:
#   chmod +x scripts/seed-all.sh
#   ./scripts/seed-all.sh [BASE_URL] [JWT_TOKEN] [DISTRIBUTOR_ID]
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${1:-http://localhost:8080/api}"
TOKEN="${2:-}"
DISTRIBUTOR_ID="${3:-a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11}"

log()  { echo "[seed-all] $*" >&2; }

log "======================================================"
log "  Zuqi Full Bootstrap"
log "  Step 1/2: seed-train.sh (synthetic data + training)"
log "  Step 2/2: tune-models.sh (hyperparameter optimisation)"
log "======================================================"

log ""
log "=== STEP 1/2: Seeding + Training ==="
bash "${SCRIPT_DIR}/seed-train.sh" "${BASE_URL}" "${TOKEN}" "${DISTRIBUTOR_ID}"

log ""
log "=== STEP 2/2: Hyperparameter Tuning ==="
bash "${SCRIPT_DIR}/tune-models.sh" "${BASE_URL}" "${TOKEN}" "${DISTRIBUTOR_ID}"

log ""
log "======================================================"
log "  Full bootstrap complete."
log "  15 models ACTIVE with optimised hyperparameters."
log "======================================================"
