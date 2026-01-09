-- Add deactivation fields to users table
ALTER TABLE users ADD COLUMN deactivation_reason VARCHAR(500);
ALTER TABLE users ADD COLUMN deactivated_at TIMESTAMP;
ALTER TABLE users ADD COLUMN deactivated_by UUID REFERENCES users(id);

-- Add deactivation fields to distributors table
ALTER TABLE distributors ADD COLUMN deactivation_reason VARCHAR(500);
ALTER TABLE distributors ADD COLUMN deactivated_at TIMESTAMP;
ALTER TABLE distributors ADD COLUMN deactivated_by UUID REFERENCES users(id);

-- Add deactivation fields to warehouses table
ALTER TABLE warehouses ADD COLUMN deactivation_reason VARCHAR(500);
ALTER TABLE warehouses ADD COLUMN deactivated_at TIMESTAMP;
ALTER TABLE warehouses ADD COLUMN deactivated_by UUID REFERENCES users(id);

-- Add deactivation fields to merchants table
ALTER TABLE merchants ADD COLUMN deactivation_reason VARCHAR(500);
ALTER TABLE merchants ADD COLUMN deactivated_at TIMESTAMP;
ALTER TABLE merchants ADD COLUMN deactivated_by UUID REFERENCES users(id);

-- Add deactivation fields to products table
ALTER TABLE products ADD COLUMN deactivation_reason VARCHAR(500);
ALTER TABLE products ADD COLUMN deactivated_at TIMESTAMP;
ALTER TABLE products ADD COLUMN deactivated_by UUID REFERENCES users(id);

-- Add active column and soft delete fields to product_categories table
ALTER TABLE product_categories ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE product_categories ADD COLUMN deactivation_reason VARCHAR(500);
ALTER TABLE product_categories ADD COLUMN deactivated_at TIMESTAMP;
ALTER TABLE product_categories ADD COLUMN deactivated_by UUID REFERENCES users(id);

-- Create indexes for better query performance on deactivation queries
CREATE INDEX idx_users_deactivated_at ON users(deactivated_at);
CREATE INDEX idx_distributors_deactivated_at ON distributors(deactivated_at);
CREATE INDEX idx_warehouses_deactivated_at ON warehouses(deactivated_at);
CREATE INDEX idx_merchants_deactivated_at ON merchants(deactivated_at);
CREATE INDEX idx_products_deactivated_at ON products(deactivated_at);
CREATE INDEX idx_product_categories_active ON product_categories(active);
CREATE INDEX idx_product_categories_deactivated_at ON product_categories(deactivated_at);
