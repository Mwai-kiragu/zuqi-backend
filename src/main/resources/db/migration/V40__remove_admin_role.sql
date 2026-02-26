-- Remove the standalone ADMIN role (keeping SUPER_ADMIN and DISTRIBUTOR_ADMIN)
-- SUPER_ADMIN now directly inherits DISTRIBUTOR_ADMIN permissions

-- 1. Remove admin@zuqi.com user's role assignment
DELETE FROM user_roles WHERE user_id = 'c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33';

-- 2. Deactivate admin@zuqi.com user
UPDATE users SET active = false WHERE id = 'c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33';

-- 3. Remove ADMIN role permissions
DELETE FROM role_permissions WHERE role_id = (SELECT id FROM roles WHERE name = 'ADMIN');

-- 4. Remove ADMIN casbin policies
DELETE FROM casbin_rule WHERE ptype = 'p' AND v0 = 'ADMIN';

-- 5. Remove old role hierarchy entries involving ADMIN
DELETE FROM casbin_rule WHERE ptype = 'g' AND (v0 = 'ADMIN' OR v1 = 'ADMIN');

-- 6. Add direct SUPER_ADMIN -> DISTRIBUTOR_ADMIN hierarchy (if not already exists)
INSERT INTO casbin_rule (ptype, v0, v1)
SELECT 'g', 'SUPER_ADMIN', 'DISTRIBUTOR_ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule WHERE ptype = 'g' AND v0 = 'SUPER_ADMIN' AND v1 = 'DISTRIBUTOR_ADMIN'
);

-- 7. Remove the ADMIN role itself
DELETE FROM roles WHERE name = 'ADMIN';
