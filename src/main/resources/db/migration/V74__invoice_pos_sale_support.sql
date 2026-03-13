-- Allow order_id and merchant_id to be nullable (invoices can now come from POS sales too)
ALTER TABLE invoices ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE invoices ALTER COLUMN merchant_id DROP NOT NULL;

-- Add POS sale reference
ALTER TABLE invoices ADD COLUMN pos_sale_id UUID REFERENCES pos_sales(id);

-- Add source type discriminator
ALTER TABLE invoices ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'ORDER';

-- Backfill existing rows
UPDATE invoices SET source_type = 'ORDER' WHERE order_id IS NOT NULL;

-- Index for quick lookup by POS sale
CREATE INDEX idx_invoices_pos_sale_id ON invoices(pos_sale_id);
