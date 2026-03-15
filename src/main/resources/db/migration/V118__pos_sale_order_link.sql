-- Allow orders created from POS sales to have no customer (walk-in)
ALTER TABLE orders ALTER COLUMN merchant_id DROP NOT NULL;

-- Track which POS sale generated this order
ALTER TABLE orders ADD COLUMN IF NOT EXISTS pos_sale_id UUID;
CREATE INDEX IF NOT EXISTS idx_orders_pos_sale ON orders(pos_sale_id);

-- Rename DRAFT → UNPAID in pos_sales status column
UPDATE pos_sales SET status = 'UNPAID' WHERE status = 'DRAFT';
