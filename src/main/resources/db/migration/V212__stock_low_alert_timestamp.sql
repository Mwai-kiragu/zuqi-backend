ALTER TABLE stock
    ADD COLUMN IF NOT EXISTS last_low_stock_alert_sent_at TIMESTAMP;
