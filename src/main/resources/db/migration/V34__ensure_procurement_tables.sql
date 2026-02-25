-- V34: Ensure supplier and procurement tables exist.
-- V32 and V33 are recorded as applied in flyway_schema_history but the tables
-- were never actually created in the current database.  Using IF NOT EXISTS makes
-- this migration safe to run even if some tables already exist.

-- ─── Supplier categories ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS supplier_categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ─── Suppliers ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS suppliers (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_code        VARCHAR(20)  NOT NULL UNIQUE,
    name                 VARCHAR(255) NOT NULL,
    kra_pin              VARCHAR(20)  UNIQUE,
    registration_number  VARCHAR(50),
    email                VARCHAR(255) UNIQUE,
    phone                VARCHAR(30)  NOT NULL UNIQUE,
    address              TEXT,
    city                 VARCHAR(100),
    county               VARCHAR(100),
    sub_county           VARCHAR(100),

    bank_name            VARCHAR(100),
    bank_branch          VARCHAR(100),
    bank_account_number  VARCHAR(50),
    bank_account_name    VARCHAR(150),
    swift_code           VARCHAR(20),

    payment_terms_days   INTEGER      NOT NULL DEFAULT 30,
    credit_limit         NUMERIC(19, 4),

    contact_persons      JSONB        NOT NULL DEFAULT '[]'::jsonb,

    category_id          BIGINT REFERENCES supplier_categories(id),
    distributor_id       UUID   REFERENCES distributors(id),

    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    verified             BOOLEAN      NOT NULL DEFAULT FALSE,
    blacklisted          BOOLEAN      NOT NULL DEFAULT FALSE,
    blacklisted_reason   TEXT,
    blacklisted_at       TIMESTAMP,
    blacklisted_by       UUID   REFERENCES users(id),

    kyc_status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    kyc_documents        JSONB,

    deactivation_reason  TEXT,
    deactivated_at       TIMESTAMP,
    deactivated_by       UUID   REFERENCES users(id),

    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_suppliers_distributor_id ON suppliers(distributor_id);
CREATE INDEX IF NOT EXISTS idx_suppliers_category_id    ON suppliers(category_id);
CREATE INDEX IF NOT EXISTS idx_suppliers_active         ON suppliers(active);
CREATE INDEX IF NOT EXISTS idx_suppliers_blacklisted    ON suppliers(blacklisted);
CREATE INDEX IF NOT EXISTS idx_suppliers_kyc_status     ON suppliers(kyc_status);

-- ─── Purchase Requisitions ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS purchase_requisitions (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    pr_number               VARCHAR(30)  NOT NULL UNIQUE,
    distributor_id          UUID         NOT NULL REFERENCES distributors(id),
    requested_by            UUID         NOT NULL REFERENCES users(id),
    status                  VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    description             TEXT,
    justification           TEXT,
    expected_delivery_date  DATE,

    items                   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    estimated_total_amount  NUMERIC(19, 4) NOT NULL DEFAULT 0,

    rejection_reason        VARCHAR(500),
    submitted_at            TIMESTAMP,
    approved_at             TIMESTAMP,
    approved_by             UUID REFERENCES users(id),

    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pr_distributor_id ON purchase_requisitions(distributor_id);
CREATE INDEX IF NOT EXISTS idx_pr_requested_by   ON purchase_requisitions(requested_by);
CREATE INDEX IF NOT EXISTS idx_pr_status         ON purchase_requisitions(status);
CREATE INDEX IF NOT EXISTS idx_pr_created_at     ON purchase_requisitions(created_at DESC);

-- ─── Purchase Orders ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS purchase_orders (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number               VARCHAR(30)  NOT NULL UNIQUE,
    supplier_id             UUID         NOT NULL REFERENCES suppliers(id),
    distributor_id          UUID         NOT NULL REFERENCES distributors(id),
    purchase_requisition_id UUID         REFERENCES purchase_requisitions(id),
    status                  VARCHAR(25)  NOT NULL DEFAULT 'DRAFT',

    items                   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    total_amount            NUMERIC(19, 4) NOT NULL DEFAULT 0,
    received_amount         NUMERIC(19, 4) NOT NULL DEFAULT 0,

    delivery_address        TEXT,
    payment_terms_days      INTEGER,
    expected_delivery_date  DATE,
    notes                   TEXT,

    sent_at                 TIMESTAMP,
    confirmed_at            TIMESTAMP,
    received_at             TIMESTAMP,
    created_by              UUID REFERENCES users(id),

    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_po_distributor_id ON purchase_orders(distributor_id);
CREATE INDEX IF NOT EXISTS idx_po_supplier_id    ON purchase_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_po_pr_id          ON purchase_orders(purchase_requisition_id);
CREATE INDEX IF NOT EXISTS idx_po_status         ON purchase_orders(status);
CREATE INDEX IF NOT EXISTS idx_po_created_at     ON purchase_orders(created_at DESC);
