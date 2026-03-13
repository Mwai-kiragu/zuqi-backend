-- Add invoice_id FK to payments so MANUAL (invoice) payments link back to their invoice
ALTER TABLE payments ADD COLUMN IF NOT EXISTS invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_payments_invoice ON payments(invoice_id);
