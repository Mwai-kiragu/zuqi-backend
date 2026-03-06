-- Assign permissions to MERCHANT_ADMIN role using module-level grants
-- Matches the backendModuleToFrontend mapping in the frontend (DASHBOARD → dashboard, etc.)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MERCHANT_ADMIN'
  AND p.module IN (
      'DASHBOARD',
      'USERS',
      'ROLES',
      'MERCHANTS',
      'DISTRIBUTORS',
      'ORDERS',
      'CUSTOMERS',
      'PRODUCTS',
      'INVENTORY',
      'WAREHOUSES',
      'PAYMENTS',
      'INVOICES',
      'CREDIT',
      'APPROVALS',
      'REPORTS',
      'PROFILE',
      'BRANCHES',
      'POS',
      'STOCK_TRANSFERS',
      'STOCK_TAKES'
  )
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
