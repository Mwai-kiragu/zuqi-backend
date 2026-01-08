-- Add is_system_role column to roles table
ALTER TABLE roles ADD COLUMN IF NOT EXISTS is_system_role BOOLEAN NOT NULL DEFAULT false;

-- Mark existing default roles as system roles
UPDATE roles SET is_system_role = true WHERE name IN ('ADMIN', 'DISTRIBUTOR_ADMIN', 'SALES_REP', 'WAREHOUSE_MANAGER', 'MERCHANT', 'FINANCE', 'DRIVER');

-- Add more default permissions for modules
INSERT INTO permissions (name, description, module) VALUES
    -- User Management
    ('users:read', 'View users', 'USERS'),
    ('users:create', 'Create users', 'USERS'),
    ('users:update', 'Update users', 'USERS'),
    ('users:delete', 'Delete users', 'USERS'),
    -- Role Management
    ('roles:read', 'View roles', 'ROLES'),
    ('roles:create', 'Create roles', 'ROLES'),
    ('roles:update', 'Update roles', 'ROLES'),
    ('roles:delete', 'Delete roles', 'ROLES'),
    -- Merchant Management
    ('merchants:read', 'View merchants', 'MERCHANTS'),
    ('merchants:create', 'Create merchants', 'MERCHANTS'),
    ('merchants:update', 'Update merchants', 'MERCHANTS'),
    ('merchants:delete', 'Delete merchants', 'MERCHANTS'),
    ('merchants:verify', 'Verify merchants', 'MERCHANTS'),
    -- Product Management
    ('products:read', 'View products', 'PRODUCTS'),
    ('products:create', 'Create products', 'PRODUCTS'),
    ('products:update', 'Update products', 'PRODUCTS'),
    ('products:delete', 'Delete products', 'PRODUCTS'),
    -- Order Management
    ('orders:read', 'View orders', 'ORDERS'),
    ('orders:create', 'Create orders', 'ORDERS'),
    ('orders:update', 'Update orders', 'ORDERS'),
    ('orders:delete', 'Cancel orders', 'ORDERS'),
    ('orders:status', 'Update order status', 'ORDERS'),
    -- Inventory Management
    ('inventory:read', 'View inventory', 'INVENTORY'),
    ('inventory:adjust', 'Adjust inventory', 'INVENTORY'),
    ('warehouses:read', 'View warehouses', 'INVENTORY'),
    ('warehouses:manage', 'Manage warehouses', 'INVENTORY'),
    -- Payment Management
    ('payments:read', 'View payments', 'PAYMENTS'),
    ('payments:create', 'Record payments', 'PAYMENTS'),
    ('payments:reconcile', 'Reconcile payments', 'PAYMENTS'),
    -- Credit Management
    ('credit:read', 'View credit', 'CREDIT'),
    ('credit:manage', 'Manage credit limits', 'CREDIT'),
    -- Reports
    ('reports:read', 'View reports', 'REPORTS'),
    ('reports:export', 'Export reports', 'REPORTS'),
    -- Dashboard
    ('dashboard:view', 'View dashboard', 'DASHBOARD'),
    -- Settings/Admin
    ('distributors:read', 'View distributors', 'ADMIN'),
    ('distributors:manage', 'Manage distributors', 'ADMIN'),
    ('settings:manage', 'Manage settings', 'ADMIN')
ON CONFLICT (name) DO NOTHING;
