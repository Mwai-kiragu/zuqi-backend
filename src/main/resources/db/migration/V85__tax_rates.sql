-- Tax Rate Management
CREATE TABLE tax_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL,
    rate DECIMAL(7,4) NOT NULL,          -- e.g. 16.0000 = 16%
    tax_type VARCHAR(30) NOT NULL DEFAULT 'PERCENTAGE',  -- PERCENTAGE, FIXED
    applies_to VARCHAR(50) NOT NULL DEFAULT 'ALL',       -- ALL, PRODUCTS, SERVICES
    is_compound BOOLEAN NOT NULL DEFAULT FALSE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    effective_from DATE,
    effective_to DATE,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    UNIQUE(distributor_id, code)
);

CREATE INDEX idx_tax_rates_distributor ON tax_rates(distributor_id);
CREATE INDEX idx_tax_rates_active ON tax_rates(active);
