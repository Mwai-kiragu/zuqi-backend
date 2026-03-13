-- Add product categories permissions to the permissions table
INSERT INTO permissions (name, description, module) VALUES
    ('products:categories:read',   'View product categories',          'PRODUCTS'),
    ('products:categories:write',  'Create/update product categories', 'PRODUCTS'),
    ('products:categories:delete', 'Delete product categories',        'PRODUCTS')
ON CONFLICT (name) DO NOTHING;

-- Assign product category permissions to SUPER_ADMIN, DISTRIBUTOR_ADMIN, MERCHANT_ADMIN (full access)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.name IN ('products:categories:read', 'products:categories:write', 'products:categories:delete')
  AND r.name IN ('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN')
ON CONFLICT DO NOTHING;

-- Assign read-only category permissions to SALES_REP, WAREHOUSE_MANAGER, CUSTOMER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.name = 'products:categories:read'
  AND r.name IN ('SALES_REP', 'WAREHOUSE_MANAGER', 'CUSTOMER', 'FINANCE')
ON CONFLICT DO NOTHING;
