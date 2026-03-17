-- Add receipt image URL column for the Zizi-style photo-upload reconciliation flow
ALTER TABLE bank_reconciliations ADD COLUMN IF NOT EXISTS receipt_image_url TEXT;
