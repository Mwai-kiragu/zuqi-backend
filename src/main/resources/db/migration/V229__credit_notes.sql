-- Add POS transaction linkage to sales returns
ALTER TABLE sales_returns
    ADD COLUMN IF NOT EXISTS pos_transaction_id UUID REFERENCES pos_sales(id);

CREATE INDEX IF NOT EXISTS idx_sales_returns_pos ON sales_returns(pos_transaction_id);

-- Credit notes: financial documents issued when a sales return is confirmed
CREATE TABLE IF NOT EXISTS credit_notes (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_note_number  VARCHAR(50)   NOT NULL UNIQUE,
    distributor_id      UUID          NOT NULL REFERENCES distributors(id),
    customer_id         UUID          REFERENCES customers(id),
    sales_return_id     UUID          REFERENCES sales_returns(id),
    source_invoice_id   UUID          REFERENCES invoices(id),
    amount              NUMERIC(15,2) NOT NULL,
    remaining_amount    NUMERIC(15,2) NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    notes               TEXT,
    expires_at          TIMESTAMP,
    created_by_id       UUID          REFERENCES users(id),
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_credit_notes_distributor   ON credit_notes(distributor_id);
CREATE INDEX IF NOT EXISTS idx_credit_notes_customer      ON credit_notes(customer_id);
CREATE INDEX IF NOT EXISTS idx_credit_notes_sales_return  ON credit_notes(sales_return_id);
CREATE INDEX IF NOT EXISTS idx_credit_notes_status        ON credit_notes(status);

-- Tracks each partial or full application of a credit note against an invoice
CREATE TABLE IF NOT EXISTS credit_note_applications (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    credit_note_id   UUID          NOT NULL REFERENCES credit_notes(id),
    invoice_id       UUID          NOT NULL REFERENCES invoices(id),
    amount_applied   NUMERIC(15,2) NOT NULL,
    applied_by_id    UUID          REFERENCES users(id),
    applied_at       TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_credit_note_apps_cn      ON credit_note_applications(credit_note_id);
CREATE INDEX IF NOT EXISTS idx_credit_note_apps_invoice ON credit_note_applications(invoice_id);

-- Permission module entries so admins can control access
INSERT INTO permissions (name, description, module) VALUES
    ('credit_notes:read',   'View credit notes',   'CREDIT_NOTES'),
    ('credit_notes:create', 'Create credit notes', 'CREDIT_NOTES'),
    ('credit_notes:update', 'Apply credit notes',  'CREDIT_NOTES'),
    ('credit_notes:delete', 'Delete credit notes', 'CREDIT_NOTES')
ON CONFLICT (name) DO NOTHING;
