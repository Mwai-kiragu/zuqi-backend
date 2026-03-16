-- Add invoices:create permission and assign to relevant roles
INSERT INTO permissions (name, description, module) VALUES
('invoices:create', 'Create manual invoices', 'INVOICES')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN', 'SALES_REP')
  AND p.name = 'invoices:create'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
