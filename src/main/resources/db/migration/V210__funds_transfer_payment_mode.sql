ALTER TABLE funds_transfers
    ADD COLUMN IF NOT EXISTS payment_mode   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS cheque_number  VARCHAR(60),
    ADD COLUMN IF NOT EXISTS cheque_date    DATE;
