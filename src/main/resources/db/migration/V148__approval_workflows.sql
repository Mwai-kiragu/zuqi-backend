-- ===========================================================================================
-- V148__approval_workflows.sql
-- Generic approval records table + approval_status columns on core entities
-- ===========================================================================================

-- Generic approval records table (polymorphic by entity_type + entity_id)
CREATE TABLE IF NOT EXISTS approval_records (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_type   VARCHAR(50)  NOT NULL,  -- ORDER, STOCK_TRANSFER, SUPPLIER_BILL, FUNDS_TRANSFER
  entity_id     UUID         NOT NULL,
  level_number  INT          NOT NULL DEFAULT 1,
  approver_id   UUID         REFERENCES users(id),
  approver_name VARCHAR(255),
  status        VARCHAR(20)  NOT NULL,  -- PENDING, APPROVED, REJECTED
  comment       TEXT,
  created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_approval_records_entity   ON approval_records(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_approval_records_approver ON approval_records(approver_id);

-- Add approval status to orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS approval_status   VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS submitted_by_id   UUID        REFERENCES users(id);

-- Add approval status to stock_transfers
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS approval_status  VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS submitted_by_id  UUID        REFERENCES users(id);

-- Add approval status to supplier_bills
ALTER TABLE supplier_bills ADD COLUMN IF NOT EXISTS approval_status   VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE supplier_bills ADD COLUMN IF NOT EXISTS submitted_by_id   UUID        REFERENCES users(id);

-- Add approval status to funds_transfers (it already has multi-level approval via ft_approvals,
-- but add a unified status column for the common API)
ALTER TABLE funds_transfers ADD COLUMN IF NOT EXISTS approval_status  VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';

COMMENT ON TABLE approval_records IS 'Generic approval audit trail for transactional entities';
COMMENT ON COLUMN approval_records.entity_type IS 'ORDER | STOCK_TRANSFER | SUPPLIER_BILL | FUNDS_TRANSFER';
COMMENT ON COLUMN approval_records.status IS 'PENDING | APPROVED | REJECTED';
