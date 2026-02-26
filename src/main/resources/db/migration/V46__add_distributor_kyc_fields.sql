ALTER TABLE distributors ADD COLUMN kyc_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE distributors ADD COLUMN kyc_documents jsonb DEFAULT '{}';
