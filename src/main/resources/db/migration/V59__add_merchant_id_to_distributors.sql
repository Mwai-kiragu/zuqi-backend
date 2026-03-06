-- Add merchant_id FK to distributors (nullable - existing distributors remain standalone)
ALTER TABLE distributors ADD COLUMN IF NOT EXISTS merchant_id UUID REFERENCES merchants(id);

CREATE INDEX IF NOT EXISTS idx_distributors_merchant ON distributors(merchant_id);
