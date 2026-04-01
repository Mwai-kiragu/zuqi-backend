-- Add tax fields to orders
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(15,2) DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_rate_name VARCHAR(100);

-- Add promotion name to order items
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS promotion_name VARCHAR(100);

-- Add promotion name to POS sale items
ALTER TABLE pos_sale_items ADD COLUMN IF NOT EXISTS promotion_name VARCHAR(100);
