-- Create new merchants table for brand/business owner entities
CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    registration_number VARCHAR(100),
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(100) NOT NULL DEFAULT 'Kenya',
    logo_url TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    kyc_documents JSONB DEFAULT '{}',
    settings JSONB DEFAULT '{}',
    deactivation_reason VARCHAR(500),
    deactivated_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_merchants_active ON merchants(active);
