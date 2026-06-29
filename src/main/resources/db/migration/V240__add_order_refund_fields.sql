-- Add refund tracking fields to orders table
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS refunded_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS refund_reason   TEXT,
    ADD COLUMN IF NOT EXISTS refunded_amount NUMERIC(15, 2);
