-- ===========================================================================================
-- V136__create_ai_bank_recon_feedback.sql
-- Part of Phase 2 — AI Bank Reconciliation Feedback Loop
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ai_bank_recon_feedback (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id          UUID NOT NULL,
    match_id                UUID,
    accepted                BOOLEAN NOT NULL DEFAULT false,
    corrected_entity_id     UUID,
    corrected_entity_type   VARCHAR(50),
    merchant_id             UUID,
    amount                  DOUBLE PRECISION,
    created_by              UUID,
    created_at              TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_ai_bank_recon_feedback_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_ai_bank_recon_feedback_merchant    FOREIGN KEY (merchant_id)    REFERENCES merchants(id),
    CONSTRAINT fk_ai_bank_recon_feedback_created_by  FOREIGN KEY (created_by)     REFERENCES users(id)
);

COMMENT ON TABLE ai_bank_recon_feedback IS 'User feedback on AI bank reconciliation matches — used as real training data';
COMMENT ON COLUMN ai_bank_recon_feedback.match_id               IS 'Reference to the AI-generated reconciliation match that was reviewed';
COMMENT ON COLUMN ai_bank_recon_feedback.accepted               IS 'Whether the user accepted the AI match suggestion';
COMMENT ON COLUMN ai_bank_recon_feedback.corrected_entity_id    IS 'Entity the user manually mapped the transaction to (if rejected)';
COMMENT ON COLUMN ai_bank_recon_feedback.corrected_entity_type  IS 'Type of the corrected entity: ORDER, PAYMENT, INVOICE, etc.';
COMMENT ON COLUMN ai_bank_recon_feedback.amount                 IS 'Transaction amount for context';

CREATE INDEX IF NOT EXISTS idx_ai_bank_recon_feedback_distributor_created
    ON ai_bank_recon_feedback (distributor_id, created_at);

CREATE INDEX IF NOT EXISTS idx_ai_bank_recon_feedback_match_id
    ON ai_bank_recon_feedback (match_id);

CREATE INDEX IF NOT EXISTS idx_ai_bank_recon_feedback_merchant
    ON ai_bank_recon_feedback (merchant_id);
