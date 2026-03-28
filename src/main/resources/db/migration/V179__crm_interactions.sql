CREATE TABLE IF NOT EXISTS customer_interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    distributor_id UUID REFERENCES distributors(id),
    interaction_type VARCHAR(20) NOT NULL,
    subject VARCHAR(255),
    notes TEXT,
    outcome VARCHAR(255),
    follow_up_date DATE,
    follow_up_done BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_id UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ci_customer_id ON customer_interactions (customer_id);
CREATE INDEX IF NOT EXISTS idx_ci_distributor_id ON customer_interactions (distributor_id);
CREATE INDEX IF NOT EXISTS idx_ci_follow_up_date ON customer_interactions (follow_up_date) WHERE follow_up_done = FALSE;

-- Casbin policies for CRM endpoints
INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', role, resource, method
FROM (VALUES
  ('SUPER_ADMIN',       '/v1/crm/interactions',        'GET'),
  ('SUPER_ADMIN',       '/v1/crm/interactions',        'POST'),
  ('SUPER_ADMIN',       '/v1/crm/interactions/.*',     'GET'),
  ('SUPER_ADMIN',       '/v1/crm/interactions/.*',     'PUT'),
  ('SUPER_ADMIN',       '/v1/crm/interactions/.*',     'DELETE'),
  ('MERCHANT_ADMIN',    '/v1/crm/interactions',        'GET'),
  ('MERCHANT_ADMIN',    '/v1/crm/interactions',        'POST'),
  ('MERCHANT_ADMIN',    '/v1/crm/interactions/.*',     'GET'),
  ('MERCHANT_ADMIN',    '/v1/crm/interactions/.*',     'PUT'),
  ('MERCHANT_ADMIN',    '/v1/crm/interactions/.*',     'DELETE'),
  ('DISTRIBUTOR_ADMIN', '/v1/crm/interactions',        'GET'),
  ('DISTRIBUTOR_ADMIN', '/v1/crm/interactions',        'POST'),
  ('DISTRIBUTOR_ADMIN', '/v1/crm/interactions/.*',     'GET'),
  ('DISTRIBUTOR_ADMIN', '/v1/crm/interactions/.*',     'PUT'),
  ('DISTRIBUTOR_ADMIN', '/v1/crm/interactions/.*',     'DELETE'),
  ('SALES_REP',         '/v1/crm/interactions',        'GET'),
  ('SALES_REP',         '/v1/crm/interactions',        'POST'),
  ('SALES_REP',         '/v1/crm/interactions/.*',     'GET'),
  ('SALES_REP',         '/v1/crm/interactions/.*',     'PUT')
) AS t(role, resource, method)
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule cr
    WHERE cr.ptype = 'p' AND cr.v0 = t.role AND cr.v1 = t.resource AND cr.v2 = t.method
);
