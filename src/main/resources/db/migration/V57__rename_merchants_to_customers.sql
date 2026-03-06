-- Rename merchant_categories to customer_categories
ALTER TABLE merchant_categories RENAME TO customer_categories;

-- Rename merchants to customers
ALTER TABLE merchants RENAME TO customers;

-- Rename indexes on customers table
ALTER INDEX IF EXISTS idx_merchants_distributor RENAME TO idx_customers_distributor;
ALTER INDEX IF EXISTS idx_merchants_category RENAME TO idx_customers_category;
ALTER INDEX IF EXISTS idx_merchants_sales_rep RENAME TO idx_customers_sales_rep;
ALTER INDEX IF EXISTS idx_merchants_active RENAME TO idx_customers_active;
ALTER INDEX IF EXISTS idx_merchants_customer_code RENAME TO idx_customers_customer_code;
ALTER INDEX IF EXISTS idx_merchants_kra_pin RENAME TO idx_customers_kra_pin;
ALTER INDEX IF EXISTS idx_merchants_blacklisted RENAME TO idx_customers_blacklisted;

-- Rename user's merchant_id column to customer_id
ALTER TABLE users RENAME COLUMN merchant_id TO customer_id;

-- Add new merchant_id column to users for brand Merchant reference
ALTER TABLE users ADD COLUMN IF NOT EXISTS merchant_id UUID;
