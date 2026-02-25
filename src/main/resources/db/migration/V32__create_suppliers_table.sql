-- V32: Create supplier categories and suppliers tables

CREATE TABLE IF NOT EXISTS supplier_categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS suppliers (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_code        VARCHAR(20) NOT NULL UNIQUE,
    name                 VARCHAR(255) NOT NULL,
    kra_pin              VARCHAR(20) UNIQUE,
    registration_number  VARCHAR(50),
    email                VARCHAR(255) UNIQUE,
    phone                VARCHAR(30) NOT NULL UNIQUE,
    address              TEXT,
    city                 VARCHAR(100),
    county               VARCHAR(100),
    sub_county           VARCHAR(100),

    -- Bank details
    bank_name            VARCHAR(100),
    bank_branch          VARCHAR(100),
    bank_account_number  VARCHAR(50),
    bank_account_name    VARCHAR(150),
    swift_code           VARCHAR(20),

    payment_terms_days   INTEGER NOT NULL DEFAULT 30,
    credit_limit         NUMERIC(19, 4),

    -- JSONB: list of {name, phone, email, role} objects
    contact_persons      JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- Relations
    category_id          BIGINT REFERENCES supplier_categories(id),
    distributor_id       UUID REFERENCES distributors(id),

    -- Status flags
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    verified             BOOLEAN NOT NULL DEFAULT FALSE,
    blacklisted          BOOLEAN NOT NULL DEFAULT FALSE,
    blacklisted_reason   TEXT,
    blacklisted_at       TIMESTAMP,
    blacklisted_by       UUID REFERENCES users(id),

    -- KYC
    kyc_status           VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    kyc_documents        JSONB,

    -- Deactivation
    deactivation_reason  TEXT,
    deactivated_at       TIMESTAMP,
    deactivated_by       UUID REFERENCES users(id),

    -- Audit
    version              BIGINT NOT NULL DEFAULT 0,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_suppliers_distributor_id    ON suppliers(distributor_id);
CREATE INDEX idx_suppliers_category_id       ON suppliers(category_id);
CREATE INDEX idx_suppliers_active            ON suppliers(active);
CREATE INDEX idx_suppliers_blacklisted       ON suppliers(blacklisted);
CREATE INDEX idx_suppliers_kyc_status        ON suppliers(kyc_status);
CREATE INDEX idx_suppliers_name_trgm         ON suppliers USING gin(name gin_trgm_ops);
