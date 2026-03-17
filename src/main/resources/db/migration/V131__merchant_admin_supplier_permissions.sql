-- V131: MERCHANT_ADMIN Supplier & Procurement Permissions

-- 1. Casbin HTTP-level rules
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'MERCHANT_ADMIN', '/v1/suppliers',              'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/suppliers',              'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/suppliers/.*',           'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/suppliers/.*',           'PUT'),
  ('p', 'MERCHANT_ADMIN', '/v1/suppliers/.*',           'DELETE'),
  ('p', 'MERCHANT_ADMIN', '/v1/suppliers/.*',           'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/suppliers/blacklisted',  'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/suppliers/categories',   'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/suppliers/categories',   'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/suppliers/categories/.*','PUT'),
  ('p', 'MERCHANT_ADMIN', '/v1/procurement/.*',         'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/procurement/.*',         'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/procurement/.*',         'PUT'),
  ('p', 'MERCHANT_ADMIN', '/v1/procurement/.*',         'DELETE'),
  ('p', 'MERCHANT_ADMIN', '/v1/supplier-bills',         'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/supplier-bills',         'POST'),
  ('p', 'MERCHANT_ADMIN', '/v1/supplier-bills/.*',      'GET'),
  ('p', 'MERCHANT_ADMIN', '/v1/supplier-bills/.*',      'PUT'),
  ('p', 'MERCHANT_ADMIN', '/v1/supplier-bills/.*',      'POST')
ON CONFLICT DO NOTHING;

-- 2. Module-level role_permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MERCHANT_ADMIN'
  AND p.module IN ('SUPPLIERS', 'PROCUREMENT')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
