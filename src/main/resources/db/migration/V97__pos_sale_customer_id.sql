ALTER TABLE pos_sales
    ADD COLUMN IF NOT EXISTS customer_id UUID REFERENCES customers(id);

CREATE INDEX IF NOT EXISTS idx_pos_sales_customer ON pos_sales(customer_id);
