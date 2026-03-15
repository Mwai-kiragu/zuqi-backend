-- entry_number was globally unique, causing collisions between distributors
-- who both generate "JE-202603-0001". Make it unique per-distributor instead.

-- Drop the global unique constraint
ALTER TABLE journal_entries DROP CONSTRAINT IF EXISTS journal_entries_entry_number_key;

-- Add composite unique: each distributor has its own sequence namespace
ALTER TABLE journal_entries
    ADD CONSTRAINT uq_journal_entry_number_per_distributor
    UNIQUE (distributor_id, entry_number);
