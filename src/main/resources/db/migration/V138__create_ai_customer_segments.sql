-- ===========================================================================================
-- V138__create_ai_customer_segments.sql
-- Part of Phase 2 — AI Customer Segmentation (K-Means)
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_customer_segments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id    UUID NOT NULL,
    customer_id       UUID NOT NULL,
    segment_id        INTEGER,
    segment_label     VARCHAR(50),
    confidence_score  DOUBLE PRECISION,
    data_phase        VARCHAR(20),
    computed_at       TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_customer_segments_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_customer_segments_customer    FOREIGN KEY (customer_id)    REFERENCES merchants(id),
    CONSTRAINT uq_ai_customer_segments_distributor_customer UNIQUE (distributor_id, customer_id)
);

COMMENT ON TABLE ai_customer_segments IS 'K-Means customer segmentation results — updated weekly';
COMMENT ON COLUMN ai_customer_segments.segment_id       IS 'Numeric cluster id assigned by the K-Means model';
COMMENT ON COLUMN ai_customer_segments.segment_label    IS 'Human-readable label: HIGH_VALUE, OCCASIONAL, AT_RISK, NEW, etc.';
COMMENT ON COLUMN ai_customer_segments.confidence_score IS 'Distance-based confidence score for cluster assignment [0.0, 1.0]';
COMMENT ON COLUMN ai_customer_segments.data_phase       IS 'Data phase at compute time: SYNTHETIC, TRANSITION, REAL';
COMMENT ON COLUMN ai_customer_segments.computed_at      IS 'Timestamp when this segmentation was last computed';

CREATE INDEX IF NOT EXISTS idx_ai_customer_segments_distributor_label
    ON ai_customer_segments (distributor_id, segment_label);

CREATE INDEX IF NOT EXISTS idx_ai_customer_segments_customer
    ON ai_customer_segments (customer_id);

CREATE INDEX IF NOT EXISTS idx_ai_customer_segments_distributor_computed
    ON ai_customer_segments (distributor_id, computed_at DESC);
