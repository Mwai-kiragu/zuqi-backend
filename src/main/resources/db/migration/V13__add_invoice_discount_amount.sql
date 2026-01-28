-- Add missing discount_amount column to invoices table
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(15, 2) DEFAULT 0;
