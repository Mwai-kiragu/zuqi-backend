-- ===========================================================================================
-- V153__ensure_approval_records.sql
-- Safety migration: ensures approval tables exist.
-- V148 created approval_records but approval_requests and approval_actions were added
-- to the domain later and their tables were never migrated.
-- All CREATE TABLE/INDEX/ALTER TABLE statements are IF NOT EXISTS — fully idempotent.
-- ===========================================================================================

-- 1. approval_records (originally from V148 — re-guard in case table was dropped)
CREATE TABLE IF NOT EXISTS approval_records (
  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_type   VARCHAR(50)  NOT NULL,
  entity_id     UUID         NOT NULL,
  level_number  INT          NOT NULL DEFAULT 1,
  approver_id   UUID         REFERENCES users(id),
  approver_name VARCHAR(255),
  status        VARCHAR(20)  NOT NULL,
  comment       TEXT,
  created_at    TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_approval_records_entity   ON approval_records(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_approval_records_approver ON approval_records(approver_id);

-- 2. approval_requests (new — maps to ApprovalRequest @Entity)
CREATE TABLE IF NOT EXISTS approval_requests (
  id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
  request_number      VARCHAR(50)    NOT NULL UNIQUE,
  workflow_type       VARCHAR(50)    NOT NULL,
  entity_type         VARCHAR(100)   NOT NULL,
  entity_id           UUID,
  entity_name         VARCHAR(255),
  requested_by_id     UUID           NOT NULL,
  requested_by_email  VARCHAR(255)   NOT NULL,
  requested_by_name   VARCHAR(255),
  status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
  description         TEXT,
  current_values      JSONB,
  requested_values    JSONB,
  required_approvals  INT            NOT NULL DEFAULT 1,
  received_approvals  INT            NOT NULL DEFAULT 0,
  amount              NUMERIC(15, 2),
  rejection_reason    TEXT,
  approved_at         TIMESTAMP,
  rejected_at         TIMESTAMP,
  cancelled_at        TIMESTAMP,
  expires_at          TIMESTAMP,
  version             BIGINT         NOT NULL DEFAULT 0,
  created_at          TIMESTAMP      NOT NULL DEFAULT now(),
  updated_at          TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_approval_requests_status    ON approval_requests(status);
CREATE INDEX IF NOT EXISTS idx_approval_requests_type      ON approval_requests(workflow_type);
CREATE INDEX IF NOT EXISTS idx_approval_requests_entity    ON approval_requests(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_approval_requests_requester ON approval_requests(requested_by_id);
CREATE INDEX IF NOT EXISTS idx_approval_requests_created   ON approval_requests(created_at);

-- 3. approval_actions (new — maps to ApprovalAction @Entity)
CREATE TABLE IF NOT EXISTS approval_actions (
  id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  approval_request_id UUID         NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
  approver_id         UUID         NOT NULL,
  approver_email      VARCHAR(255) NOT NULL,
  approver_name       VARCHAR(255),
  decision            VARCHAR(20)  NOT NULL,
  approval_level      INT          NOT NULL DEFAULT 1,
  comments            TEXT,
  action_at           TIMESTAMP    NOT NULL,
  created_at          TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_approval_actions_request  ON approval_actions(approval_request_id);
CREATE INDEX IF NOT EXISTS idx_approval_actions_approver ON approval_actions(approver_id);

-- 4. Approval status columns on core entities (idempotent — all IF NOT EXISTS)
ALTER TABLE orders          ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE orders          ADD COLUMN IF NOT EXISTS submitted_by_id UUID        REFERENCES users(id);
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS submitted_by_id UUID        REFERENCES users(id);
ALTER TABLE supplier_bills  ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE supplier_bills  ADD COLUMN IF NOT EXISTS submitted_by_id UUID        REFERENCES users(id);
ALTER TABLE funds_transfers ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';
