-- ═══════════════════════════════════════════════════════════════
-- 1. CASBIN RULES — HTTP-level access for /v1/funds-transfers/**
-- ═══════════════════════════════════════════════════════════════
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  -- SUPER_ADMIN: read only
  ('p', 'SUPER_ADMIN',       '/v1/funds-transfers',                        'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/funds-transfers/.*',                     'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/funds-transfers/amount-ranges',          'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/funds-transfers/amount-ranges/.*',       'GET'),

  -- DISTRIBUTOR_ADMIN: full access
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers',                        'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers',                        'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/.*',                     'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/.*',                     'PUT'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/.*',                     'DELETE'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/.*/submit',              'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/.*/approve',             'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/.*/reject',              'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/.*/cancel',              'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/amount-ranges',          'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/amount-ranges',          'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/amount-ranges/.*',       'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/amount-ranges/.*',       'PUT'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/funds-transfers/amount-ranges/.*',       'DELETE'),

  -- MERCHANT_ADMIN: full access
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers',                        'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers',                        'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/.*',                     'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/.*',                     'PUT'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/.*',                     'DELETE'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/.*/submit',              'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/.*/approve',             'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/.*/reject',              'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/.*/cancel',              'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/amount-ranges',          'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/amount-ranges',          'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/amount-ranges/.*',       'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/amount-ranges/.*',       'PUT'),
  ('p', 'MERCHANT_ADMIN',    '/v1/funds-transfers/amount-ranges/.*',       'DELETE'),

  -- FINANCE: create, read, update + approve/reject/cancel (no delete)
  ('p', 'FINANCE',           '/v1/funds-transfers',                        'GET'),
  ('p', 'FINANCE',           '/v1/funds-transfers',                        'POST'),
  ('p', 'FINANCE',           '/v1/funds-transfers/.*',                     'GET'),
  ('p', 'FINANCE',           '/v1/funds-transfers/.*',                     'PUT'),
  ('p', 'FINANCE',           '/v1/funds-transfers/.*/submit',              'POST'),
  ('p', 'FINANCE',           '/v1/funds-transfers/.*/approve',             'POST'),
  ('p', 'FINANCE',           '/v1/funds-transfers/.*/reject',              'POST'),
  ('p', 'FINANCE',           '/v1/funds-transfers/.*/cancel',              'POST'),
  ('p', 'FINANCE',           '/v1/funds-transfers/amount-ranges',          'GET'),
  ('p', 'FINANCE',           '/v1/funds-transfers/amount-ranges/.*',       'GET')

ON CONFLICT DO NOTHING;


-- ═══════════════════════════════════════════════════════════════
-- 2. PERMISSION RECORDS (permissions table)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO permissions (name, description, module) VALUES
  ('funds_transfers:read',    'View funds transfers',                  'FUNDS_TRANSFERS'),
  ('funds_transfers:write',   'Create and update funds transfers',     'FUNDS_TRANSFERS'),
  ('funds_transfers:delete',  'Delete funds transfers',                'FUNDS_TRANSFERS'),
  ('funds_transfers:approve', 'Approve or reject funds transfers',     'FUNDS_TRANSFERS')
ON CONFLICT DO NOTHING;


-- ═══════════════════════════════════════════════════════════════
-- 3. ROLE → PERMISSION ASSIGNMENTS
-- ═══════════════════════════════════════════════════════════════

-- SUPER_ADMIN: read only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN'
  AND p.name IN ('funds_transfers:read')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- DISTRIBUTOR_ADMIN: full access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'DISTRIBUTOR_ADMIN'
  AND p.module = 'FUNDS_TRANSFERS'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- MERCHANT_ADMIN: full access
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MERCHANT_ADMIN'
  AND p.module = 'FUNDS_TRANSFERS'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- FINANCE: read, write, approve (no delete)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'FINANCE'
  AND p.name IN ('funds_transfers:read', 'funds_transfers:write', 'funds_transfers:approve')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ═══════════════════════════════════════════════════════════════
-- 4. BILLING MODULE REGISTRATION
-- ═══════════════════════════════════════════════════════════════
INSERT INTO billing_modules (module_key, display_name, description, sort_order) VALUES
  ('fundsTransfer', 'Funds Transfers', 'Multi-level approval workflow for bank transfers', 24)
ON CONFLICT (module_key) DO NOTHING;

-- Add fundsTransfer to GOLD package
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","customers","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","gl","ai","credit","pos","branches","stockTransfers","stockTakes","bankReconciliation","taxRates","expenses","fundsTransfer"]'
WHERE name = 'GOLD';

-- Add fundsTransfer to SILVER package
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","customers","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","pos","branches","stockTransfers","stockTakes","bankReconciliation","taxRates","expenses","fundsTransfer"]'
WHERE name = 'SILVER';
