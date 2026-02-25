-- General Ledger Tables
-- V35: Create GL accounts, periods, cost centers, journal entries, and budgets

-- ─── Chart of Accounts ────────────────────────────────────────────────────────
CREATE TABLE gl_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    account_code    VARCHAR(20)  NOT NULL,
    account_name    VARCHAR(200) NOT NULL,
    account_type    VARCHAR(30)  NOT NULL,
    account_sub_type VARCHAR(40) NOT NULL,
    normal_balance  VARCHAR(10)  NOT NULL,
    parent_id       UUID REFERENCES gl_accounts(id) ON DELETE SET NULL,
    level           INT          NOT NULL DEFAULT 1,
    is_posting_account BOOLEAN   NOT NULL DEFAULT TRUE,
    is_system_account  BOOLEAN   NOT NULL DEFAULT FALSE,
    description     TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    CONSTRAINT uq_gl_account_code_distributor UNIQUE (distributor_id, account_code)
);

CREATE INDEX idx_gl_accounts_distributor ON gl_accounts(distributor_id);
CREATE INDEX idx_gl_accounts_parent     ON gl_accounts(parent_id);
CREATE INDEX idx_gl_accounts_type       ON gl_accounts(account_type);
CREATE INDEX idx_gl_accounts_active     ON gl_accounts(active);

-- ─── Accounting Periods ───────────────────────────────────────────────────────
CREATE TABLE gl_periods (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    period_name     VARCHAR(30)  NOT NULL,
    period_year     INT          NOT NULL,
    period_month    INT          NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    closed_at       TIMESTAMP,
    closed_by       UUID REFERENCES users(id),
    locked_at       TIMESTAMP,
    locked_by       UUID REFERENCES users(id),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    CONSTRAINT uq_gl_period_distributor_ym UNIQUE (distributor_id, period_year, period_month)
);

CREATE INDEX idx_gl_periods_distributor ON gl_periods(distributor_id);
CREATE INDEX idx_gl_periods_status      ON gl_periods(status);
CREATE INDEX idx_gl_periods_year_month  ON gl_periods(period_year, period_month);

-- ─── Cost Centres ─────────────────────────────────────────────────────────────
CREATE TABLE cost_centers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    parent_id       UUID REFERENCES cost_centers(id) ON DELETE SET NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    CONSTRAINT uq_cost_center_code_distributor UNIQUE (distributor_id, code)
);

CREATE INDEX idx_cost_centers_distributor ON cost_centers(distributor_id);
CREATE INDEX idx_cost_centers_active      ON cost_centers(active);

-- ─── Journal Entries ──────────────────────────────────────────────────────────
CREATE TABLE journal_entries (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id        UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    entry_number          VARCHAR(30)  NOT NULL UNIQUE,
    period_id             UUID NOT NULL REFERENCES gl_periods(id),
    entry_date            DATE         NOT NULL,
    description           TEXT         NOT NULL,
    reference             VARCHAR(100),
    source_module         VARCHAR(30)  NOT NULL DEFAULT 'MANUAL',
    source_document_id    UUID,
    status                VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    total_debit           NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_credit          NUMERIC(18,2) NOT NULL DEFAULT 0,
    is_reversal           BOOLEAN      NOT NULL DEFAULT FALSE,
    reversal_of_entry_id  UUID REFERENCES journal_entries(id),
    reversed_by_entry_id  UUID REFERENCES journal_entries(id),
    posted_at             TIMESTAMP,
    posted_by             UUID REFERENCES users(id),
    rejected_at           TIMESTAMP,
    rejection_reason      TEXT,
    created_by            UUID REFERENCES users(id),
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP
);

CREATE INDEX idx_journal_entries_distributor   ON journal_entries(distributor_id);
CREATE INDEX idx_journal_entries_period        ON journal_entries(period_id);
CREATE INDEX idx_journal_entries_status        ON journal_entries(status);
CREATE INDEX idx_journal_entries_entry_date    ON journal_entries(entry_date);
CREATE INDEX idx_journal_entries_source_module ON journal_entries(source_module);
CREATE INDEX idx_journal_entries_source_doc    ON journal_entries(source_document_id);

-- ─── Journal Entry Lines ──────────────────────────────────────────────────────
CREATE TABLE journal_entry_lines (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    line_number      INT  NOT NULL,
    account_id       UUID NOT NULL REFERENCES gl_accounts(id),
    cost_center_id   UUID REFERENCES cost_centers(id),
    description      VARCHAR(500),
    debit_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    credit_amount    NUMERIC(18,2) NOT NULL DEFAULT 0,
    reference        VARCHAR(100),
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jel_journal_entry ON journal_entry_lines(journal_entry_id);
CREATE INDEX idx_jel_account       ON journal_entry_lines(account_id);
CREATE INDEX idx_jel_cost_center   ON journal_entry_lines(cost_center_id);

-- ─── Budgets ──────────────────────────────────────────────────────────────────
CREATE TABLE budgets (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id   UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    budget_year      INT  NOT NULL,
    period_month     INT  NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    account_id       UUID NOT NULL REFERENCES gl_accounts(id),
    cost_center_id   UUID REFERENCES cost_centers(id),
    budgeted_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    notes            TEXT,
    created_by       UUID REFERENCES users(id),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP,
    CONSTRAINT uq_budget_distributor_year_month_account_cc
        UNIQUE (distributor_id, budget_year, period_month, account_id, cost_center_id)
);

CREATE INDEX idx_budgets_distributor    ON budgets(distributor_id);
CREATE INDEX idx_budgets_year_month     ON budgets(budget_year, period_month);
CREATE INDEX idx_budgets_account        ON budgets(account_id);
