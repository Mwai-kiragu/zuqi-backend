-- Add PAYMENT_SETUP as a dedicated permission module
-- Covers /v1/mpesa/** endpoints

-- ═══════════════════════════════════════════════════════════════
-- 1. INSERT PERMISSIONS
-- ═══════════════════════════════════════════════════════════════
INSERT INTO permissions (name, description, module) VALUES
    ('payment_setup:read',   'View payment configurations',          'PAYMENT_SETUP'),
    ('payment_setup:write',  'Create/update payment configurations', 'PAYMENT_SETUP'),
    ('payment_setup:delete', 'Delete/deactivate payment configs',    'PAYMENT_SETUP')
ON CONFLICT DO NOTHING;


-- ═══════════════════════════════════════════════════════════════
-- 2. SUPER_ADMIN — full access
-- ═══════════════════════════════════════════════════════════════
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN'
  AND p.module = 'PAYMENT_SETUP'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ═══════════════════════════════════════════════════════════════
-- 3. MERCHANT_ADMIN — full access (owns the config)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MERCHANT_ADMIN'
  AND p.module = 'PAYMENT_SETUP'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ═══════════════════════════════════════════════════════════════
-- 4. DISTRIBUTOR_ADMIN — read only
-- ═══════════════════════════════════════════════════════════════
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'DISTRIBUTOR_ADMIN'
  AND p.name = 'payment_setup:read'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ═══════════════════════════════════════════════════════════════
-- 5. FINANCE — read only
-- ═══════════════════════════════════════════════════════════════
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'FINANCE'
  AND p.name = 'payment_setup:read'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
