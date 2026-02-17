-- V24: Create merchant_credit_outcomes table for real data tracking
-- Blueprint: ML_IMPLEMENTATION_PLAN.md - Phase 3, Task 8

CREATE TABLE merchant_credit_outcomes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    credit_application_id UUID,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('DEFAULT', 'NO_DEFAULT')),
    outcome_date TIMESTAMP NOT NULL,
    reason VARCHAR(500),
    recorded_by UUID,  -- Admin user ID or NULL for system
    used_for_training BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for query performance
CREATE INDEX idx_merchant_credit_outcomes_merchant_id ON merchant_credit_outcomes(merchant_id);
CREATE INDEX idx_merchant_credit_outcomes_outcome ON merchant_credit_outcomes(outcome);
CREATE INDEX idx_merchant_credit_outcomes_used_for_training ON merchant_credit_outcomes(used_for_training);
CREATE INDEX idx_merchant_credit_outcomes_outcome_date ON merchant_credit_outcomes(outcome_date DESC);

-- Composite index for retraining queries
CREATE INDEX idx_merchant_credit_outcomes_unused ON merchant_credit_outcomes(used_for_training, created_at)
    WHERE used_for_training = FALSE;

-- Comments
COMMENT ON TABLE merchant_credit_outcomes IS 'Real merchant credit outcomes for ML model retraining';
COMMENT ON COLUMN merchant_credit_outcomes.outcome IS 'DEFAULT or NO_DEFAULT - actual credit outcome';
COMMENT ON COLUMN merchant_credit_outcomes.used_for_training IS 'Whether this outcome has been used in model retraining';
COMMENT ON COLUMN merchant_credit_outcomes.recorded_by IS 'User ID who recorded outcome (NULL = system)';
COMMENT ON COLUMN merchant_credit_outcomes.reason IS 'Audit trail for why outcome occurred';
