-- ===========================================================================================
-- V147__create_ai_reorder_suggestions.sql
-- Part of Phase 2 — Inventory AI: Reorder Optimization
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_reorder_suggestions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id              UUID NOT NULL,
    warehouse_id                UUID NOT NULL,
    product_id                  UUID NOT NULL,
    supplier_id                 UUID,
    suggested_qty               DOUBLE PRECISION NOT NULL,
    economic_order_qty          DOUBLE PRECISION,
    safety_stock                DOUBLE PRECISION,
    reorder_point               DOUBLE PRECISION,
    current_stock               DOUBLE PRECISION,
    days_of_supply_remaining    DOUBLE PRECISION,
    avg_daily_demand            DOUBLE PRECISION,
    lead_time_days              DOUBLE PRECISION,
    confidence_score            DOUBLE PRECISION,
    data_phase                  VARCHAR(20),
    status                      VARCHAR(20) DEFAULT 'PENDING',
    model_version               INTEGER,
    converted_pr_id             UUID,
    computed_at                 TIMESTAMP DEFAULT NOW(),
    created_at                  TIMESTAMP DEFAULT NOW(),
    updated_at                  TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_reorder_suggestions_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_reorder_suggestions_warehouse   FOREIGN KEY (warehouse_id)   REFERENCES warehouses(id),
    CONSTRAINT fk_ai_reorder_suggestions_product     FOREIGN KEY (product_id)     REFERENCES products(id)
);

COMMENT ON TABLE ai_reorder_suggestions IS 'EOQ-based reorder suggestions generated daily — triggers PR/PO creation in REAL data phase';
COMMENT ON COLUMN ai_reorder_suggestions.suggested_qty              IS 'Recommended order quantity (EOQ or adjusted)';
COMMENT ON COLUMN ai_reorder_suggestions.economic_order_qty         IS 'Raw EOQ from Wilson formula';
COMMENT ON COLUMN ai_reorder_suggestions.safety_stock               IS 'Safety stock buffer computed from demand and lead time variability';
COMMENT ON COLUMN ai_reorder_suggestions.reorder_point              IS 'Stock level at which reorder is triggered';
COMMENT ON COLUMN ai_reorder_suggestions.days_of_supply_remaining   IS 'Estimated days until stockout at current demand rate';
COMMENT ON COLUMN ai_reorder_suggestions.confidence_score           IS 'Confidence [0.0, 1.0]; reduced in SYNTHETIC data phase';
COMMENT ON COLUMN ai_reorder_suggestions.data_phase                 IS 'Data phase at compute time: SYNTHETIC, TRANSITION, REAL';
COMMENT ON COLUMN ai_reorder_suggestions.status                     IS 'Workflow status: PENDING, APPROVED, CONVERTED, REJECTED, EXPIRED';
COMMENT ON COLUMN ai_reorder_suggestions.converted_pr_id            IS 'ID of the PurchaseRequisition created from this suggestion';

CREATE INDEX IF NOT EXISTS idx_ai_reorder_suggestions_distributor_warehouse
    ON ai_reorder_suggestions (distributor_id, warehouse_id);

CREATE INDEX IF NOT EXISTS idx_ai_reorder_suggestions_distributor_product
    ON ai_reorder_suggestions (distributor_id, product_id);

CREATE INDEX IF NOT EXISTS idx_ai_reorder_suggestions_status
    ON ai_reorder_suggestions (distributor_id, status);

CREATE INDEX IF NOT EXISTS idx_ai_reorder_suggestions_computed_at
    ON ai_reorder_suggestions (computed_at DESC);
