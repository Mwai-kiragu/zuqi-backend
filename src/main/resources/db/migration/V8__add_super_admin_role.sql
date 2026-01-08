-- Add SUPER_ADMIN role - the highest level administrator
-- SUPER_ADMIN can:
-- 1. Access everything in the system
-- 2. Create and manage ADMINs
-- 3. Create distributors and assign admins to them
-- 4. See all data across all distributors

-- Create SUPER_ADMIN role
INSERT INTO roles (name, description, is_system_role)
VALUES ('SUPER_ADMIN', 'Super Administrator with full system access. Can create and manage all admins and distributors.', true);

-- Give SUPER_ADMIN all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'SUPER_ADMIN';

-- Create SUPER_ADMIN user (password: Password123)
INSERT INTO users (id, email, password, first_name, last_name, phone_number, active, email_verified, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'superadmin@zuqi.com',
    '$2a$10$icg2rXIoHoFJQ1B9jnZpxeH2RG99B7mE.qpvMmBtdY0BseVCvGs5K',
    'Super',
    'Admin',
    '+254700000000',
    true,
    true,
    NOW(),
    NOW()
);

-- Assign SUPER_ADMIN role to the user
INSERT INTO user_roles (user_id, role_id)
SELECT 'a0000000-0000-0000-0000-000000000001'::uuid, id FROM roles WHERE name = 'SUPER_ADMIN';

-- Update Casbin policies for SUPER_ADMIN
-- SUPER_ADMIN has full access to everything (even more than ADMIN)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/*', '.*');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/*', '.*');

-- Update role hierarchy: SUPER_ADMIN > ADMIN > DISTRIBUTOR_ADMIN > others
-- First remove old ADMIN -> DISTRIBUTOR_ADMIN hierarchy
DELETE FROM casbin_rule WHERE ptype = 'g' AND v0 = 'ADMIN' AND v1 = 'DISTRIBUTOR_ADMIN';

-- Add new hierarchy
INSERT INTO casbin_rule (ptype, v0, v1) VALUES ('g', 'SUPER_ADMIN', 'ADMIN');
INSERT INTO casbin_rule (ptype, v0, v1) VALUES ('g', 'ADMIN', 'DISTRIBUTOR_ADMIN');

-- SUPER_ADMIN profile access
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/users/me', 'GET|PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/users/me/change-password', 'POST');
