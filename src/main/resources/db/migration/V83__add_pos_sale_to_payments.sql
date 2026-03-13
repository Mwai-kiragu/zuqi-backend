-- source_type tracks whether payment came from an order, POS sale, or manual recording
ALTER TABLE payments ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

-- Make merchant_id nullable — POS walk-in sales have no registered merchant account
ALTER TABLE payments ALTER COLUMN merchant_id DROP NOT NULL;

-- Link payments back to the originating POS sale
ALTER TABLE payments ADD COLUMN pos_sale_id UUID REFERENCES pos_sales(id) ON DELETE SET NULL;
CREATE INDEX idx_payments_pos_sale ON payments(pos_sale_id);

-- Back-fill existing order-linked payments
UPDATE payments SET source_type = 'ORDER' WHERE order_id IS NOT NULL;
