-- Add comprehensive permissions for ALL frontend modules
-- This ensures the admin panel (EditRolePage) can control access to every sidebar module

-- ============================================================
-- 1. Insert new permissions for modules missing from V1
-- ============================================================

INSERT INTO permissions (name, description, module) VALUES
    -- PRODUCTS
    ('products:read',   'View products',           'PRODUCTS'),
    ('products:write',  'Create/update products',  'PRODUCTS'),
    ('products:delete', 'Delete products',         'PRODUCTS'),

    -- INVOICES
    ('invoices:read',   'View invoices',           'INVOICES'),
    ('invoices:write',  'Create/update invoices',  'INVOICES'),
    ('invoices:delete', 'Delete invoices',         'INVOICES'),

    -- WAREHOUSES
    ('warehouses:read',  'View warehouses',          'WAREHOUSES'),
    ('warehouses:write', 'Create/update warehouses', 'WAREHOUSES'),

    -- DISTRIBUTORS
    ('distributors:read',   'View distributors',           'DISTRIBUTORS'),
    ('distributors:write',  'Create/update distributors',  'DISTRIBUTORS'),
    ('distributors:delete', 'Delete distributors',         'DISTRIBUTORS'),

    -- ROLES
    ('roles:read',   'View roles & permissions', 'ROLES'),
    ('roles:write',  'Create/update roles',      'ROLES'),
    ('roles:delete', 'Delete roles',             'ROLES'),

    -- SUPPLIERS
    ('suppliers:read',   'View suppliers',           'SUPPLIERS'),
    ('suppliers:write',  'Create/update suppliers',  'SUPPLIERS'),
    ('suppliers:delete', 'Delete suppliers',         'SUPPLIERS'),

    -- PROCUREMENT
    ('procurement:read',   'View purchase requisitions & orders', 'PROCUREMENT'),
    ('procurement:write',  'Create/update procurement docs',      'PROCUREMENT'),
    ('procurement:delete', 'Delete procurement docs',             'PROCUREMENT'),

    -- GENERAL LEDGER
    ('gl:read',   'View GL accounts, journals, periods', 'GENERAL_LEDGER'),
    ('gl:write',  'Create/update GL entries',            'GENERAL_LEDGER'),
    ('gl:delete', 'Delete GL entries',                   'GENERAL_LEDGER'),

    -- APPROVALS
    ('approvals:read',  'View approval requests',    'APPROVALS'),
    ('approvals:write', 'Process approval requests', 'APPROVALS'),

    -- BILLING
    ('billing:read',  'View billing & subscriptions',   'BILLING'),
    ('billing:write', 'Manage billing & subscriptions', 'BILLING'),

    -- DASHBOARD
    ('dashboard:read', 'View dashboard', 'DASHBOARD'),

    -- AUDIT LOGS
    ('audit_logs:read', 'View audit logs', 'AUDIT_LOGS'),

    -- SALES TEAM
    ('sales_team:read',  'View sales team',   'SALES_TEAM'),
    ('sales_team:write', 'Manage sales team', 'SALES_TEAM'),

    -- PROFILE (all users)
    ('profile:read',  'View own profile',  'PROFILE'),
    ('profile:write', 'Update own profile', 'PROFILE')

ON CONFLICT DO NOTHING;


-- ============================================================
-- 2. Assign permissions to roles (matching current static map)
-- ============================================================

-- SUPER_ADMIN: gets ALL permissions (existing + new)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- DISTRIBUTOR_ADMIN: dashboard, orders, merchants, products, inventory, warehouses,
--   payments, invoices, reports, approvals, suppliers, procurement, gl, profile
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'DISTRIBUTOR_ADMIN'
  AND p.module IN ('DASHBOARD', 'ORDERS', 'MERCHANTS', 'PRODUCTS', 'INVENTORY', 'WAREHOUSES',
                   'PAYMENTS', 'INVOICES', 'REPORTS', 'APPROVALS', 'SUPPLIERS', 'PROCUREMENT',
                   'GENERAL_LEDGER', 'PROFILE')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- SALES_REP: dashboard, orders, merchants, products, invoices, reports, approvals, profile
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SALES_REP'
  AND p.module IN ('DASHBOARD', 'ORDERS', 'MERCHANTS', 'PRODUCTS', 'INVOICES', 'REPORTS',
                   'APPROVALS', 'PROFILE')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- WAREHOUSE_MANAGER: dashboard, inventory, warehouses, products, orders, reports,
--   approvals, procurement, profile
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'WAREHOUSE_MANAGER'
  AND p.module IN ('DASHBOARD', 'INVENTORY', 'WAREHOUSES', 'PRODUCTS', 'ORDERS', 'REPORTS',
                   'APPROVALS', 'PROCUREMENT', 'PROFILE')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- MERCHANT: dashboard, orders, products, payments, invoices, profile
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MERCHANT'
  AND p.module IN ('DASHBOARD', 'ORDERS', 'PRODUCTS', 'PAYMENTS', 'INVOICES', 'PROFILE')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- FINANCE: dashboard, payments, invoices, credit, merchants, reports, approvals,
--   suppliers, procurement, gl, profile
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'FINANCE'
  AND p.module IN ('DASHBOARD', 'PAYMENTS', 'INVOICES', 'CREDIT', 'MERCHANTS', 'REPORTS',
                   'APPROVALS', 'SUPPLIERS', 'PROCUREMENT', 'GENERAL_LEDGER', 'PROFILE')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- DRIVER: dashboard, orders, profile
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'DRIVER'
  AND p.module IN ('DASHBOARD', 'ORDERS', 'PROFILE')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
