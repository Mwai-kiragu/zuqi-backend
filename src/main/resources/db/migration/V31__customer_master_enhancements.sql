-- Customer Master Enhancements
-- Adds KYC, blacklisting, customer code, and Kenya-specific fields to merchants
-- Part of Phase 2: Customer Master Data

ALTER TABLE merchants ADD COLUMN IF NOT EXISTS customer_code     VARCHAR(20)  NOT NULL DEFAULT '';
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS kra_pin           VARCHAR(20);
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS county            VARCHAR(100);
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS sub_county        VARCHAR(100);
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS contact_persons   JSONB        NOT NULL DEFAULT '[]';
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS blacklisted       BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS blacklisted_reason VARCHAR(500);
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS blacklisted_at    TIMESTAMP;
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS blacklisted_by    UUID;
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS kyc_status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING';
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS kyc_documents     JSONB        NOT NULL DEFAULT '{}';

-- Comments for documentation
COMMENT ON COLUMN merchants.customer_code    IS 'Unique distributor-assigned customer code (e.g. DIST-0001)';
COMMENT ON COLUMN merchants.kra_pin          IS 'Kenya Revenue Authority PIN for tax compliance';
COMMENT ON COLUMN merchants.county           IS 'Kenya county (e.g. Nairobi, Mombasa, Kisumu)';
COMMENT ON COLUMN merchants.sub_county       IS 'Kenya sub-county for route planning';
COMMENT ON COLUMN merchants.contact_persons  IS 'JSON: [{name, phone, role}, ...] - additional contacts';
COMMENT ON COLUMN merchants.blacklisted      IS 'TRUE if merchant is blacklisted and cannot place orders';
COMMENT ON COLUMN merchants.kyc_status       IS 'KYC verification state: PENDING / VERIFIED / REJECTED';
COMMENT ON COLUMN merchants.kyc_documents    IS 'JSON: document references for KYC verification';

-- Indexes for customer master queries
CREATE INDEX IF NOT EXISTS idx_merchants_customer_code
    ON merchants(customer_code);

CREATE INDEX IF NOT EXISTS idx_merchants_kra_pin
    ON merchants(kra_pin) WHERE kra_pin IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_merchants_blacklisted
    ON merchants(blacklisted) WHERE blacklisted = TRUE;

CREATE INDEX IF NOT EXISTS idx_merchants_kyc_status
    ON merchants(kyc_status);

CREATE INDEX IF NOT EXISTS idx_merchants_deactivated_at
    ON merchants(deactivated_at);
