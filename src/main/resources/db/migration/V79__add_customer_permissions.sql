-- Add customer permissions (replacing old 'merchant' module permissions)
INSERT INTO permissions (name, description, module) VALUES
    ('customer:read',   'View customers',   'CUSTOMERS'),
    ('customer:create', 'Create customers', 'CUSTOMERS'),
    ('customer:update', 'Update customers', 'CUSTOMERS'),
    ('customer:delete', 'Delete customers', 'CUSTOMERS')
ON CONFLICT (name) DO NOTHING;

-- Update existing merchant module permissions to CUSTOMERS module
UPDATE permissions SET module = 'CUSTOMERS' WHERE module = 'merchant';

-- Assign new customer permissions to DISTRIBUTOR_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'DISTRIBUTOR_ADMIN'
  AND p.name IN ('customer:read', 'customer:create', 'customer:update', 'customer:delete')
ON CONFLICT DO NOTHING;

-- Assign to SALES_REP
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SALES_REP'
  AND p.name IN ('customer:read', 'customer:create', 'customer:update')
ON CONFLICT DO NOTHING;

-- Assign to MERCHANT_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MERCHANT_ADMIN'
  AND p.name IN ('customer:read', 'customer:create', 'customer:update', 'customer:delete')
ON CONFLICT DO NOTHING;

-- Assign to FINANCE
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'FINANCE'
  AND p.name IN ('customer:read')
ON CONFLICT DO NOTHING;
