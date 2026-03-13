-- Seed test users for development
-- Password for all users: Password123
-- BCrypt hash: $2a$10$icg2rXIoHoFJQ1B9jnZpxeH2RG99B7mE.qpvMmBtdY0BseVCvGs5K

-- Create a test distributor
INSERT INTO distributors (id, name, registration_number, email, phone, address, city, country)
VALUES (
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'Nairobi Beverages Ltd',
    'BV-2024-001',
    'info@nairobibeverages.co.ke',
    '+254700000001',
    '123 Industrial Area',
    'Nairobi',
    'Kenya'
) ON CONFLICT (id) DO NOTHING;

-- Create a warehouse for the distributor
INSERT INTO warehouses (id, distributor_id, name, code, address, city)
VALUES (
    'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22',
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'Main Warehouse',
    'WH-001',
    '123 Industrial Area',
    'Nairobi'
) ON CONFLICT (id) DO NOTHING;

-- Create test users for each role
-- 1. ADMIN user
INSERT INTO users (id, first_name, last_name, email, phone_number, password, active, email_verified)
VALUES (
    'c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33',
    'Admin',
    'User',
    'admin@zuqi.com',
    '+254700000002',
    '$2a$10$icg2rXIoHoFJQ1B9jnZpxeH2RG99B7mE.qpvMmBtdY0BseVCvGs5K',
    true,
    true
) ON CONFLICT (id) DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33', id FROM roles WHERE name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- 2. DISTRIBUTOR_ADMIN user
INSERT INTO users (id, first_name, last_name, email, phone_number, password, active, email_verified, distributor_id)
VALUES (
    'd3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44',
    'Distributor',
    'Admin',
    'distributor@zuqi.com',
    '+254700000003',
    '$2a$10$icg2rXIoHoFJQ1B9jnZpxeH2RG99B7mE.qpvMmBtdY0BseVCvGs5K',
    true,
    true,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44', id FROM roles WHERE name = 'DISTRIBUTOR_ADMIN'
ON CONFLICT DO NOTHING;

-- 3. SALES_REP user
INSERT INTO users (id, first_name, last_name, email, phone_number, password, active, email_verified, distributor_id)
VALUES (
    'e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
    'Sales',
    'Representative',
    'sales@zuqi.com',
    '+254700000004',
    '$2a$10$icg2rXIoHoFJQ1B9jnZpxeH2RG99B7mE.qpvMmBtdY0BseVCvGs5K',
    true,
    true,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a55', id FROM roles WHERE name = 'SALES_REP'
ON CONFLICT DO NOTHING;

-- 4. WAREHOUSE_MANAGER user
INSERT INTO users (id, first_name, last_name, email, phone_number, password, active, email_verified, distributor_id)
VALUES (
    'f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66',
    'Warehouse',
    'Manager',
    'warehouse@zuqi.com',
    '+254700000005',
    '$2a$10$icg2rXIoHoFJQ1B9jnZpxeH2RG99B7mE.qpvMmBtdY0BseVCvGs5K',
    true,
    true,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66', id FROM roles WHERE name = 'WAREHOUSE_MANAGER'
ON CONFLICT DO NOTHING;

-- 5. MERCHANT user
INSERT INTO users (id, first_name, last_name, email, phone_number, password, active, email_verified)
VALUES (
    'a6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77',
    'Merchant',
    'Owner',
    'merchant@zuqi.com',
    '+254700000006',
    '$2a$10$icg2rXIoHoFJQ1B9jnZpxeH2RG99B7mE.qpvMmBtdY0BseVCvGs5K',
    true,
    true
) ON CONFLICT (id) DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'a6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77', id FROM roles WHERE name = 'MERCHANT'
ON CONFLICT DO NOTHING;

-- Create a merchant business for the merchant user
INSERT INTO merchants (id, business_name, owner_name, email, phone, address, city, category_id, distributor_id, assigned_sales_rep_id, customer_code)
VALUES (
    'b7eebc99-9c0b-4ef8-bb6d-6bb9bd380a88',
    'Kawangware Groceries',
    'Merchant Owner',
    'merchant@zuqi.com',
    '+254700000006',
    '45 Kawangware Road',
    'Nairobi',
    (SELECT id FROM merchant_categories WHERE name = 'Duka/Kiosk'),
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
    'e4eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
    'CUST-0001'
) ON CONFLICT (id) DO NOTHING;

-- Update merchant user with merchant_id
UPDATE users SET merchant_id = 'b7eebc99-9c0b-4ef8-bb6d-6bb9bd380a88'
WHERE id = 'a6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77';

-- 6. FINANCE user
INSERT INTO users (id, first_name, last_name, email, phone_number, password, active, email_verified, distributor_id)
VALUES (
    'c8eebc99-9c0b-4ef8-bb6d-6bb9bd380a99',
    'Finance',
    'Officer',
    'finance@zuqi.com',
    '+254700000007',
    '$2a$10$icg2rXIoHoFJQ1B9jnZpxeH2RG99B7mE.qpvMmBtdY0BseVCvGs5K',
    true,
    true,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'c8eebc99-9c0b-4ef8-bb6d-6bb9bd380a99', id FROM roles WHERE name = 'FINANCE'
ON CONFLICT DO NOTHING;

-- 7. DRIVER user
INSERT INTO users (id, first_name, last_name, email, phone_number, password, active, email_verified, distributor_id)
VALUES (
    'd9eebc99-9c0b-4ef8-bb6d-6bb9bd380aaa',
    'Delivery',
    'Driver',
    'driver@zuqi.com',
    '+254700000008',
    '$2a$10$icg2rXIoHoFJQ1B9jnZpxeH2RG99B7mE.qpvMmBtdY0BseVCvGs5K',
    true,
    true,
    'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
) ON CONFLICT (id) DO NOTHING;
INSERT INTO user_roles (user_id, role_id)
SELECT 'd9eebc99-9c0b-4ef8-bb6d-6bb9bd380aaa', id FROM roles WHERE name = 'DRIVER'
ON CONFLICT DO NOTHING;

-- Add some sample products
INSERT INTO products (id, distributor_id, sku, name, description, unit_price, cost_price)
VALUES
    ('01eebc99-9c0b-4ef8-bb6d-6bb9bd380b11', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'BEV-001', 'Coca Cola 500ml', 'Coca Cola soft drink 500ml bottle', 80.00, 60.00),
    ('02eebc99-9c0b-4ef8-bb6d-6bb9bd380b22', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'BEV-002', 'Fanta Orange 500ml', 'Fanta Orange soft drink 500ml bottle', 80.00, 60.00),
    ('03eebc99-9c0b-4ef8-bb6d-6bb9bd380b33', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'BEV-003', 'Sprite 500ml', 'Sprite soft drink 500ml bottle', 80.00, 60.00),
    ('04eebc99-9c0b-4ef8-bb6d-6bb9bd380b44', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'WAT-001', 'Dasani Water 1L', 'Dasani mineral water 1 liter', 60.00, 40.00),
    ('05eebc99-9c0b-4ef8-bb6d-6bb9bd380b55', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'JUI-001', 'Minute Maid 1L', 'Minute Maid orange juice 1 liter', 150.00, 110.00)
ON CONFLICT (id) DO NOTHING;

-- Add stock to warehouse
INSERT INTO stock (warehouse_id, product_id, quantity, reorder_level)
VALUES
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', '01eebc99-9c0b-4ef8-bb6d-6bb9bd380b11', 500, 100),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', '02eebc99-9c0b-4ef8-bb6d-6bb9bd380b22', 450, 100),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', '03eebc99-9c0b-4ef8-bb6d-6bb9bd380b33', 400, 100),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', '04eebc99-9c0b-4ef8-bb6d-6bb9bd380b44', 300, 50),
    ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22', '05eebc99-9c0b-4ef8-bb6d-6bb9bd380b55', 200, 50)
ON CONFLICT DO NOTHING;
