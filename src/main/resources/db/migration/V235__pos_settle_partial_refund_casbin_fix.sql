-- V235: Re-apply Casbin policies for POS settle-balance and partial-refund.
-- V234 used a correlated WHERE NOT EXISTS which may not have applied cleanly.
-- This migration uses ON CONFLICT DO NOTHING (same pattern as V56) to be idempotent.

INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'DISTRIBUTOR_ADMIN',  '/v1/pos/sales/*/settle',        'POST'),
('p', 'WAREHOUSE_MANAGER',  '/v1/pos/sales/*/settle',        'POST'),
('p', 'SALES_REP',          '/v1/pos/sales/*/settle',        'POST'),
('p', 'MERCHANT_ADMIN',     '/v1/pos/sales/*/settle',        'POST'),
('p', 'DISTRIBUTOR_ADMIN',  '/v1/pos/sales/*/partial-refund','POST'),
('p', 'WAREHOUSE_MANAGER',  '/v1/pos/sales/*/partial-refund','POST'),
('p', 'SALES_REP',          '/v1/pos/sales/*/partial-refund','POST'),
('p', 'MERCHANT_ADMIN',     '/v1/pos/sales/*/partial-refund','POST')
ON CONFLICT DO NOTHING;
