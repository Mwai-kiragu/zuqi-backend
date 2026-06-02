ALTER TABLE funds_transfers
    ADD COLUMN IF NOT EXISTS cheque_image_url VARCHAR(500);
