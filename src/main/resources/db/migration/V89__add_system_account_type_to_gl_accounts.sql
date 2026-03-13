-- Add system_account_type column to gl_accounts so distributors can tag their
-- chart-of-accounts entries for automatic GL posting.
ALTER TABLE gl_accounts
    ADD COLUMN IF NOT EXISTS system_account_type VARCHAR(50);

-- Also add source_module and source_document_id to journal_entries if missing
-- (these columns exist on the entity but may not have been in the original DDL)
ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS source_document_id UUID;

CREATE INDEX IF NOT EXISTS idx_gl_accounts_system_type
    ON gl_accounts(distributor_id, system_account_type);

CREATE INDEX IF NOT EXISTS idx_journal_entries_source
    ON journal_entries(distributor_id, source_module, source_document_id);
