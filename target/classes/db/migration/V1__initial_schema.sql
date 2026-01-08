-- Zuqi Platform Initial Schema
-- Version: 1.0
-- Date: December 2025

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enable pgvector extension for AI embeddings
CREATE EXTENSION IF NOT EXISTS vector;

-- ===========================================
-- USER MANAGEMENT TABLES
-- ===========================================

-- Permissions table for fine-grained access control
CREATE TABLE permissions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    module VARCHAR(50)
);

-- Roles table
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Role-Permission mapping
CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(20),
    password VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    email_verified BOOLEAN NOT NULL DEFAULT false,
    phone_verified BOOLEAN NOT NULL DEFAULT false,
    profile_image_url VARCHAR(500),
    distributor_id UUID,
    merchant_id UUID,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_users_active ON users(active);
CREATE INDEX idx_users_distributor ON users(distributor_id);

-- User-Role mapping
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Refresh tokens table
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);

-- ===========================================
-- DISTRIBUTOR & WAREHOUSE TABLES
-- ===========================================

-- Distributors table
CREATE TABLE distributors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    registration_number VARCHAR(100),
    tax_id VARCHAR(100),
    email VARCHAR(255),
    phone VARCHAR(20),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(100) DEFAULT 'Kenya',
    logo_url VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT true,
    settings JSONB DEFAULT '{}',
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_distributors_active ON distributors(active);

-- Warehouses table
CREATE TABLE warehouses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    distributor_id UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    manager_id UUID REFERENCES users(id),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_warehouses_distributor ON warehouses(distributor_id);

-- ===========================================
-- MERCHANT TABLES
-- ===========================================

-- Merchant categories
CREATE TABLE merchant_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Merchants table (retail outlets)
CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_name VARCHAR(255) NOT NULL,
    owner_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(20) NOT NULL,
    address TEXT,
    city VARCHAR(100),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    category_id BIGINT REFERENCES merchant_categories(id),
    distributor_id UUID REFERENCES distributors(id),
    assigned_sales_rep_id UUID REFERENCES users(id),
    route_id UUID,
    credit_limit DECIMAL(15, 2) DEFAULT 0,
    current_balance DECIMAL(15, 2) DEFAULT 0,
    payment_terms_days INTEGER DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    verified BOOLEAN NOT NULL DEFAULT false,
    metadata JSONB DEFAULT '{}',
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merchants_distributor ON merchants(distributor_id);
CREATE INDEX idx_merchants_category ON merchants(category_id);
CREATE INDEX idx_merchants_sales_rep ON merchants(assigned_sales_rep_id);
CREATE INDEX idx_merchants_active ON merchants(active);

-- ===========================================
-- PRODUCT & INVENTORY TABLES
-- ===========================================

-- Product categories
CREATE TABLE product_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT REFERENCES product_categories(id),
    description VARCHAR(255),
    distributor_id UUID REFERENCES distributors(id)
);

-- Products table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    distributor_id UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category_id BIGINT REFERENCES product_categories(id),
    unit_of_measure VARCHAR(50) DEFAULT 'PIECE',
    unit_price DECIMAL(15, 2) NOT NULL,
    cost_price DECIMAL(15, 2),
    tax_rate DECIMAL(5, 2) DEFAULT 0,
    image_url VARCHAR(500),
    barcode VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT true,
    metadata JSONB DEFAULT '{}',
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(distributor_id, sku)
);

CREATE INDEX idx_products_distributor ON products(distributor_id);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_sku ON products(sku);

-- Stock table (inventory per warehouse)
CREATE TABLE stock (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    warehouse_id UUID NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity DECIMAL(15, 3) NOT NULL DEFAULT 0,
    reserved_quantity DECIMAL(15, 3) NOT NULL DEFAULT 0,
    reorder_level DECIMAL(15, 3),
    last_stock_check TIMESTAMP,
    version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(warehouse_id, product_id)
);

CREATE INDEX idx_stock_warehouse ON stock(warehouse_id);
CREATE INDEX idx_stock_product ON stock(product_id);

-- Stock movements
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    product_id UUID NOT NULL REFERENCES products(id),
    movement_type VARCHAR(50) NOT NULL, -- IN, OUT, ADJUSTMENT, TRANSFER
    quantity DECIMAL(15, 3) NOT NULL,
    reference_type VARCHAR(50), -- ORDER, PURCHASE, ADJUSTMENT, RETURN
    reference_id UUID,
    notes TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stock_movements_warehouse ON stock_movements(warehouse_id);
CREATE INDEX idx_stock_movements_product ON stock_movements(product_id);
CREATE INDEX idx_stock_movements_type ON stock_movements(movement_type);

-- ===========================================
-- SALES & ORDER TABLES
-- ===========================================

-- Routes for sales reps
CREATE TABLE routes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    distributor_id UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    assigned_rep_id UUID REFERENCES users(id),
    schedule JSONB, -- Schedule pattern
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_routes_distributor ON routes(distributor_id);
CREATE INDEX idx_routes_rep ON routes(assigned_rep_id);

-- Orders table
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_number VARCHAR(50) NOT NULL UNIQUE,
    distributor_id UUID NOT NULL REFERENCES distributors(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    sales_rep_id UUID REFERENCES users(id),
    warehouse_id UUID REFERENCES warehouses(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    order_type VARCHAR(50) DEFAULT 'STANDARD', -- STANDARD, CREDIT, PRE_ORDER
    subtotal DECIMAL(15, 2) NOT NULL,
    tax_amount DECIMAL(15, 2) DEFAULT 0,
    discount_amount DECIMAL(15, 2) DEFAULT 0,
    total_amount DECIMAL(15, 2) NOT NULL,
    paid_amount DECIMAL(15, 2) DEFAULT 0,
    payment_status VARCHAR(50) DEFAULT 'PENDING', -- PENDING, PARTIAL, PAID
    payment_terms_days INTEGER DEFAULT 0,
    payment_due_date DATE,
    delivery_address TEXT,
    delivery_latitude DECIMAL(10, 8),
    delivery_longitude DECIMAL(11, 8),
    notes TEXT,
    metadata JSONB DEFAULT '{}',
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_distributor ON orders(distributor_id);
CREATE INDEX idx_orders_merchant ON orders(merchant_id);
CREATE INDEX idx_orders_sales_rep ON orders(sales_rep_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);
CREATE INDEX idx_orders_created ON orders(created_at);

-- Order items
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity DECIMAL(15, 3) NOT NULL,
    unit_price DECIMAL(15, 2) NOT NULL,
    discount_percent DECIMAL(5, 2) DEFAULT 0,
    tax_amount DECIMAL(15, 2) DEFAULT 0,
    total_amount DECIMAL(15, 2) NOT NULL
);

CREATE INDEX idx_order_items_order ON order_items(order_id);

-- Order status history
CREATE TABLE order_status_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    notes TEXT,
    changed_by UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_status_history_order ON order_status_history(order_id);

-- Deliveries
CREATE TABLE deliveries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id),
    driver_id UUID REFERENCES users(id),
    vehicle_id UUID,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    scheduled_date DATE,
    scheduled_time_start TIME,
    scheduled_time_end TIME,
    actual_delivery_time TIMESTAMP,
    signature_url VARCHAR(500),
    proof_of_delivery_url VARCHAR(500),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_deliveries_order ON deliveries(order_id);
CREATE INDEX idx_deliveries_driver ON deliveries(driver_id);
CREATE INDEX idx_deliveries_status ON deliveries(status);

-- ===========================================
-- PAYMENT TABLES
-- ===========================================

-- Payment methods
CREATE TABLE payment_methods (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT true
);

-- Payments table
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_number VARCHAR(50) NOT NULL UNIQUE,
    order_id UUID REFERENCES orders(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    distributor_id UUID NOT NULL REFERENCES distributors(id),
    payment_method_id BIGINT REFERENCES payment_methods(id),
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'KES',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    external_reference VARCHAR(255), -- KCB/M-Pesa reference
    transaction_id VARCHAR(255),
    payment_date TIMESTAMP,
    reconciled BOOLEAN NOT NULL DEFAULT false,
    reconciled_at TIMESTAMP,
    reconciled_by UUID REFERENCES users(id),
    notes TEXT,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payments_order ON payments(order_id);
CREATE INDEX idx_payments_merchant ON payments(merchant_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_reconciled ON payments(reconciled);

-- Payment transactions (detailed transaction log)
CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_id UUID NOT NULL REFERENCES payments(id),
    transaction_type VARCHAR(50) NOT NULL, -- INITIATE, CONFIRM, REVERSE, REFUND
    amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    gateway_response JSONB,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_transactions_payment ON payment_transactions(payment_id);

-- ===========================================
-- CREDIT MANAGEMENT TABLES
-- ===========================================

-- Credit scores
CREATE TABLE credit_scores (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    score DECIMAL(5, 2) NOT NULL,
    score_grade VARCHAR(10), -- A, B, C, D, F
    factors JSONB NOT NULL, -- Scoring factors and weights
    model_version VARCHAR(50),
    valid_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_credit_scores_merchant ON credit_scores(merchant_id);

-- Credit limits
CREATE TABLE credit_limits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    distributor_id UUID NOT NULL REFERENCES distributors(id),
    approved_limit DECIMAL(15, 2) NOT NULL,
    utilized_amount DECIMAL(15, 2) DEFAULT 0,
    available_limit DECIMAL(15, 2) NOT NULL,
    interest_rate DECIMAL(5, 2) DEFAULT 0, -- Monthly rate
    approved_by UUID REFERENCES users(id),
    approved_at TIMESTAMP,
    expires_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_credit_limits_merchant ON credit_limits(merchant_id);
CREATE INDEX idx_credit_limits_distributor ON credit_limits(distributor_id);

-- Credit applications
CREATE TABLE credit_applications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    distributor_id UUID NOT NULL REFERENCES distributors(id),
    requested_amount DECIMAL(15, 2) NOT NULL,
    approved_amount DECIMAL(15, 2),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    credit_score_id UUID REFERENCES credit_scores(id),
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMP,
    review_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_credit_applications_merchant ON credit_applications(merchant_id);
CREATE INDEX idx_credit_applications_status ON credit_applications(status);

-- ===========================================
-- INVOICE FINANCING TABLES
-- ===========================================

-- Invoices
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    distributor_id UUID NOT NULL REFERENCES distributors(id),
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    order_id UUID REFERENCES orders(id),
    amount DECIMAL(15, 2) NOT NULL,
    paid_amount DECIMAL(15, 2) DEFAULT 0,
    due_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_invoices_distributor ON invoices(distributor_id);
CREATE INDEX idx_invoices_merchant ON invoices(merchant_id);
CREATE INDEX idx_invoices_status ON invoices(status);

-- Invoice discounting requests
CREATE TABLE invoice_discounting_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    distributor_id UUID NOT NULL REFERENCES distributors(id),
    total_invoice_amount DECIMAL(15, 2) NOT NULL,
    advance_amount DECIMAL(15, 2) NOT NULL,
    discount_rate DECIMAL(5, 2) NOT NULL,
    discount_fee DECIMAL(15, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    approved_by UUID REFERENCES users(id),
    approved_at TIMESTAMP,
    disbursed_at TIMESTAMP,
    settled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_invoice_discounting_distributor ON invoice_discounting_requests(distributor_id);

-- Invoice discounting items
CREATE TABLE invoice_discounting_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    request_id UUID NOT NULL REFERENCES invoice_discounting_requests(id) ON DELETE CASCADE,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    amount DECIMAL(15, 2) NOT NULL
);

-- ===========================================
-- AI CONFIGURATION TABLES
-- ===========================================

-- AI configurations
CREATE TABLE ai_configurations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value JSONB NOT NULL,
    category VARCHAR(50) NOT NULL, -- model, prompt, rag, cache
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_configurations_key ON ai_configurations(config_key);
CREATE INDEX idx_ai_configurations_category ON ai_configurations(category);

-- System configurations
CREATE TABLE system_configurations (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===========================================
-- SEED DATA
-- ===========================================

-- Insert default roles
INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'System administrator with full access'),
    ('DISTRIBUTOR_ADMIN', 'Distributor company administrator'),
    ('SALES_REP', 'Field sales representative'),
    ('WAREHOUSE_MANAGER', 'Warehouse manager responsible for inventory'),
    ('MERCHANT', 'Retail merchant/shop owner'),
    ('FINANCE', 'Finance team member for payment reconciliation'),
    ('DRIVER', 'Delivery driver');

-- Insert default permissions
INSERT INTO permissions (name, description, module) VALUES
    ('user:read', 'View users', 'user'),
    ('user:write', 'Create/update users', 'user'),
    ('user:delete', 'Delete users', 'user'),
    ('order:read', 'View orders', 'order'),
    ('order:write', 'Create/update orders', 'order'),
    ('order:delete', 'Delete orders', 'order'),
    ('payment:read', 'View payments', 'payment'),
    ('payment:write', 'Process payments', 'payment'),
    ('payment:reconcile', 'Reconcile payments', 'payment'),
    ('inventory:read', 'View inventory', 'inventory'),
    ('inventory:write', 'Manage inventory', 'inventory'),
    ('credit:read', 'View credit information', 'credit'),
    ('credit:write', 'Manage credit limits', 'credit'),
    ('credit:approve', 'Approve credit applications', 'credit'),
    ('merchant:read', 'View merchants', 'merchant'),
    ('merchant:write', 'Manage merchants', 'merchant'),
    ('report:read', 'View reports', 'report'),
    ('report:export', 'Export reports', 'report'),
    ('settings:read', 'View settings', 'settings'),
    ('settings:write', 'Manage settings', 'settings');

-- Assign permissions to roles
-- Admin gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ADMIN';

-- Distributor Admin permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'DISTRIBUTOR_ADMIN'
AND p.name IN ('user:read', 'user:write', 'order:read', 'order:write', 'payment:read',
               'inventory:read', 'inventory:write', 'credit:read', 'credit:write',
               'merchant:read', 'merchant:write', 'report:read', 'report:export');

-- Sales Rep permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SALES_REP'
AND p.name IN ('order:read', 'order:write', 'merchant:read', 'merchant:write',
               'inventory:read', 'credit:read');

-- Warehouse Manager permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'WAREHOUSE_MANAGER'
AND p.name IN ('order:read', 'inventory:read', 'inventory:write', 'report:read');

-- Merchant permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MERCHANT'
AND p.name IN ('order:read', 'order:write', 'payment:read', 'credit:read');

-- Finance permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'FINANCE'
AND p.name IN ('payment:read', 'payment:write', 'payment:reconcile', 'report:read', 'report:export');

-- Insert default payment methods
INSERT INTO payment_methods (name, code, description) VALUES
    ('Cash', 'CASH', 'Cash on delivery'),
    ('M-Pesa', 'MPESA', 'Safaricom M-Pesa mobile money'),
    ('KCB Bank Transfer', 'KCB_TRANSFER', 'KCB bank transfer'),
    ('KCB Vooma', 'KCB_VOOMA', 'KCB Vooma mobile money'),
    ('Credit', 'CREDIT', 'Credit/trade terms');

-- Insert default merchant categories
INSERT INTO merchant_categories (name, description) VALUES
    ('Supermarket', 'Large retail store selling groceries and household items'),
    ('Duka/Kiosk', 'Small retail shop'),
    ('Wholesale', 'Wholesale distributor'),
    ('Pharmacy', 'Pharmacy and medical supplies'),
    ('Hardware', 'Hardware and construction materials'),
    ('Restaurant/Hotel', 'Food service establishment'),
    ('Other', 'Other business type');

-- Insert default AI configurations
INSERT INTO ai_configurations (config_key, config_value, category, description) VALUES
    ('credit.scoring.model', '{"provider": "openai", "model": "gpt-4", "temperature": 0.3, "maxTokens": 1000}', 'model', 'Credit scoring AI model configuration'),
    ('credit.scoring.weights', '{"orderHistory": 0.3, "paymentHistory": 0.35, "businessProfile": 0.2, "creditUtilization": 0.15}', 'model', 'Credit scoring factor weights'),
    ('rag.chunking', '{"chunkSize": 1000, "chunkOverlap": 200}', 'rag', 'RAG document chunking configuration'),
    ('cache.embeddings.ttl', '{"hours": 24}', 'cache', 'Embedding cache TTL');
