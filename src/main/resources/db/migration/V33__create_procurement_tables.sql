-- V33: Create purchase requisitions and purchase orders tables

CREATE TABLE IF NOT EXISTS purchase_requisitions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pr_number               VARCHAR(30) NOT NULL UNIQUE,
    distributor_id          UUID NOT NULL REFERENCES distributors(id),
    requested_by            UUID NOT NULL REFERENCES users(id),
    status                  VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    description             TEXT,
    justification           TEXT,
    expected_delivery_date  DATE,

    -- Line items stored as JSONB array
    items                   JSONB NOT NULL DEFAULT '[]'::jsonb,
    estimated_total_amount  NUMERIC(19, 4) NOT NULL DEFAULT 0,

    rejection_reason        TEXT,
    submitted_at            TIMESTAMP,
    approved_at             TIMESTAMP,
    approved_by             UUID REFERENCES users(id),

    -- Audit
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS purchase_orders (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number               VARCHAR(30) NOT NULL UNIQUE,
    supplier_id             UUID NOT NULL REFERENCES suppliers(id),
    distributor_id          UUID NOT NULL REFERENCES distributors(id),
    purchase_requisition_id UUID REFERENCES purchase_requisitions(id),
    status                  VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

    -- Line items stored as JSONB array
    items                   JSONB NOT NULL DEFAULT '[]'::jsonb,
    total_amount            NUMERIC(19, 4) NOT NULL DEFAULT 0,
    received_amount         NUMERIC(19, 4) NOT NULL DEFAULT 0,

    delivery_address        TEXT,
    payment_terms_days      INTEGER NOT NULL DEFAULT 30,
    expected_delivery_date  DATE,
    notes                   TEXT,

    sent_at                 TIMESTAMP,
    confirmed_at            TIMESTAMP,
    received_at             TIMESTAMP,
    created_by              UUID REFERENCES users(id),

    -- Audit
    version                 BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_pr_distributor_id  ON purchase_requisitions(distributor_id);
CREATE INDEX IF NOT EXISTS idx_pr_requested_by    ON purchase_requisitions(requested_by);
CREATE INDEX IF NOT EXISTS idx_pr_status          ON purchase_requisitions(status);
CREATE INDEX IF NOT EXISTS idx_pr_created_at      ON purchase_requisitions(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_po_distributor_id  ON purchase_orders(distributor_id);
CREATE INDEX IF NOT EXISTS idx_po_supplier_id     ON purchase_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_po_pr_id           ON purchase_orders(purchase_requisition_id);
CREATE INDEX IF NOT EXISTS idx_po_status          ON purchase_orders(status);
CREATE INDEX IF NOT EXISTS idx_po_created_at      ON purchase_orders(created_at DESC);
