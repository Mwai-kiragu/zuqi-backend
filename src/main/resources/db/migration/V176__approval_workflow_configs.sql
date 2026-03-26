-- Approval Workflow Configs: defines WHO approves at each LEVEL per workflow type
CREATE TABLE IF NOT EXISTS approval_workflow_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id UUID REFERENCES distributors(id) ON DELETE CASCADE,
    workflow_type VARCHAR(60) NOT NULL,
    level_number INT NOT NULL DEFAULT 1,
    role_label VARCHAR(100) NOT NULL,       -- e.g. "Accountant", "General Manager", "Directors"
    required_role VARCHAR(60),              -- Optional Casbin role key (FINANCE, SALES_REP, etc.)
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_workflow_config UNIQUE (distributor_id, workflow_type, level_number)
);

CREATE INDEX IF NOT EXISTS idx_awc_distributor_workflow
    ON approval_workflow_configs (distributor_id, workflow_type);

-- Casbin policies for /v1/approval-configs/**
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', role, resource, method
FROM (VALUES
  ('SUPER_ADMIN',       '/v1/approval-configs',    'GET'),
  ('SUPER_ADMIN',       '/v1/approval-configs',    'POST'),
  ('SUPER_ADMIN',       '/v1/approval-configs/.*', 'GET'),
  ('SUPER_ADMIN',       '/v1/approval-configs/.*', 'PUT'),
  ('SUPER_ADMIN',       '/v1/approval-configs/.*', 'DELETE'),
  ('MERCHANT_ADMIN',    '/v1/approval-configs',    'GET'),
  ('MERCHANT_ADMIN',    '/v1/approval-configs',    'POST'),
  ('MERCHANT_ADMIN',    '/v1/approval-configs/.*', 'GET'),
  ('MERCHANT_ADMIN',    '/v1/approval-configs/.*', 'PUT'),
  ('MERCHANT_ADMIN',    '/v1/approval-configs/.*', 'DELETE'),
  ('DISTRIBUTOR_ADMIN', '/v1/approval-configs',    'GET'),
  ('DISTRIBUTOR_ADMIN', '/v1/approval-configs',    'POST'),
  ('DISTRIBUTOR_ADMIN', '/v1/approval-configs/.*', 'GET'),
  ('DISTRIBUTOR_ADMIN', '/v1/approval-configs/.*', 'PUT'),
  ('DISTRIBUTOR_ADMIN', '/v1/approval-configs/.*', 'DELETE')
) AS t(role, resource, method)
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule cr
    WHERE cr.ptype = 'p' AND cr.v0 = t.role AND cr.v1 = t.resource AND cr.v2 = t.method
);
