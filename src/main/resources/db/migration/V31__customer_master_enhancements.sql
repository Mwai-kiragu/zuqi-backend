-- V27: Customer Master Enhancements (Priority 3)
-- Adds: customer_code (auto-generated), KRA PIN, county/sub-county,
--       contact persons (JSONB), blacklisting, KYC status/documents

-- Add new columns to merchants table
ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS customer_code VARCHAR(20) UNIQUE,
    ADD COLUMN IF NOT EXISTS kra_pin VARCHAR(20) UNIQUE,
    ADD COLUMN IF NOT EXISTS county VARCHAR(100),
    ADD COLUMN IF NOT EXISTS sub_county VARCHAR(100),
    ADD COLUMN IF NOT EXISTS contact_persons JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS blacklisted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS blacklisted_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS blacklisted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS blacklisted_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS kyc_documents JSONB NOT NULL DEFAULT '{}'::jsonb;

-- Backfill customer codes for existing merchants using a sequence-based approach
DO $$
DECLARE
    rec RECORD;
    counter INTEGER := 1;
BEGIN
    FOR rec IN SELECT id FROM merchants ORDER BY created_at ASC NULLS LAST LOOP
        UPDATE merchants SET customer_code = 'CUST-' || LPAD(counter::TEXT, 5, '0') WHERE id = rec.id;
        counter := counter + 1;
    END LOOP;
END $$;

-- Make customer_code NOT NULL after backfill
ALTER TABLE merchants ALTER COLUMN customer_code SET NOT NULL;

-- Add indexes for new columns
CREATE INDEX IF NOT EXISTS idx_merchants_customer_code ON merchants(customer_code);
CREATE INDEX IF NOT EXISTS idx_merchants_kra_pin ON merchants(kra_pin) WHERE kra_pin IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_merchants_blacklisted ON merchants(blacklisted) WHERE blacklisted = TRUE;
CREATE INDEX IF NOT EXISTS idx_merchants_kyc_status ON merchants(kyc_status);

-- Add foreign key constraint for blacklisted_by
ALTER TABLE merchants
    ADD CONSTRAINT fk_merchants_blacklisted_by
    FOREIGN KEY (blacklisted_by) REFERENCES users(id) ON DELETE SET NULL;
