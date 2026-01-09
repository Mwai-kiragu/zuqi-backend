-- Add missing columns to invoices table
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS balance_due DECIMAL(15, 2);

-- Add other columns that might be missing
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS idx_invoices_order VARCHAR(50);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS idx_invoices_due_date DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS idx_invoices_created TIMESTAMP;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS idx_invoices_invoice_number VARCHAR(50);

-- Drop the incorrectly named columns if they exist (they were index names, not columns)
ALTER TABLE invoices DROP COLUMN IF EXISTS idx_invoices_order;
ALTER TABLE invoices DROP COLUMN IF EXISTS idx_invoices_due_date;
ALTER TABLE invoices DROP COLUMN IF EXISTS idx_invoices_created;
ALTER TABLE invoices DROP COLUMN IF EXISTS idx_invoices_invoice_number;

-- Create indexes if they don't exist
CREATE INDEX IF NOT EXISTS idx_invoices_order ON invoices(order_id);
CREATE INDEX IF NOT EXISTS idx_invoices_due_date ON invoices(due_date);
CREATE INDEX IF NOT EXISTS idx_invoices_created ON invoices(created_at);
CREATE INDEX IF NOT EXISTS idx_invoices_invoice_number ON invoices(invoice_number);
