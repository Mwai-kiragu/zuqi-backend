-- Funds Transfer amount range configuration
CREATE TABLE ft_amount_ranges (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id   UUID NOT NULL,
    name             VARCHAR(100) NOT NULL,
    min_amount       DECIMAL(15,2) NOT NULL DEFAULT 0,
    max_amount       DECIMAL(15,2),               -- NULL = no upper limit
    required_levels  INT NOT NULL DEFAULT 1,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Approvers per level per amount range
CREATE TABLE ft_approval_levels (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amount_range_id UUID NOT NULL REFERENCES ft_amount_ranges(id) ON DELETE CASCADE,
    level_number    INT NOT NULL,
    level_name      VARCHAR(100),
    approver_user_id UUID NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Main funds transfer record
CREATE TABLE funds_transfers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id          UUID NOT NULL,
    reference_number        VARCHAR(60) UNIQUE,
    transfer_type           VARCHAR(20) NOT NULL DEFAULT 'EXTERNAL',
    debit_account_number    VARCHAR(100),
    debit_bank_name         VARCHAR(100),
    credit_account_number   VARCHAR(100) NOT NULL,
    credit_bank_name        VARCHAR(100),
    amount                  DECIMAL(15,2) NOT NULL,
    currency                VARCHAR(10) NOT NULL DEFAULT 'KES',
    description             TEXT,
    payment_details         TEXT,
    status                  VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    current_approval_level  INT NOT NULL DEFAULT 1,
    required_approval_levels INT NOT NULL DEFAULT 1,
    amount_range_id         UUID REFERENCES ft_amount_ranges(id),
    initiator_id            UUID NOT NULL,
    disbursed_at            TIMESTAMP,
    rejected_reason         TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Individual approval decisions
CREATE TABLE ft_approvals (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES funds_transfers(id) ON DELETE CASCADE,
    level_number INT NOT NULL,
    approver_id UUID NOT NULL,
    status      VARCHAR(20) NOT NULL,             -- APPROVED, REJECTED
    comment     TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_ft_amount_ranges_dist ON ft_amount_ranges(distributor_id);
CREATE INDEX idx_funds_transfers_dist  ON funds_transfers(distributor_id);
CREATE INDEX idx_funds_transfers_stat  ON funds_transfers(distributor_id, status);
CREATE INDEX idx_ft_approvals_transfer ON ft_approvals(transfer_id);
CREATE INDEX idx_ft_approval_levels_range ON ft_approval_levels(amount_range_id);
