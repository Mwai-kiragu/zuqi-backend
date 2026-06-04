-- Add missing Casbin policies for /v1/gl/budgets/bulk endpoint
-- The existing /v1/gl/budgets policy does not match the /bulk sub-path via keyMatch

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/budgets/bulk', '.*'),
('p', 'FINANCE',           '/v1/gl/budgets/bulk', 'POST'),
('p', 'MERCHANT_ADMIN',    '/v1/gl/budgets/bulk', '.*')
ON CONFLICT DO NOTHING;
