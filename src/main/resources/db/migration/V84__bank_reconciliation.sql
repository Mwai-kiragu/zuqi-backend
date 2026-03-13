-- Bank Reconciliation
CREATE TABLE bank_reconciliations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    account_name VARCHAR(200) NOT NULL,
    account_number VARCHAR(50),
    bank_name VARCHAR(200),
    statement_date DATE NOT NULL,
    statement_balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    system_balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    adjusted_bank_balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    adjusted_system_balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    difference DECIMAL(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes TEXT,
    reconciled_by UUID REFERENCES users(id),
    reconciled_at TIMESTAMP,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE TABLE bank_reconciliation_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_id UUID NOT NULL REFERENCES bank_reconciliations(id) ON DELETE CASCADE,
    item_type VARCHAR(30) NOT NULL,  -- OUTSTANDING_CHECK, DEPOSIT_IN_TRANSIT, BANK_ERROR, BOOK_ERROR, OTHER
    description VARCHAR(500) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    transaction_date DATE,
    reference VARCHAR(100),
    is_cleared BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bank_recon_distributor ON bank_reconciliations(distributor_id);
CREATE INDEX idx_bank_recon_date ON bank_reconciliations(statement_date);
CREATE INDEX idx_bank_recon_status ON bank_reconciliations(status);
CREATE INDEX idx_bank_recon_items ON bank_reconciliation_items(reconciliation_id);
