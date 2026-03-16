-- Allow SUPER_ADMIN, DISTRIBUTOR_ADMIN, MERCHANT_ADMIN and SALES_REP to create manual invoices
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
('p', 'SUPER_ADMIN',        '/v1/invoices', 'POST'),
('p', 'DISTRIBUTOR_ADMIN',  '/v1/invoices', 'POST'),
('p', 'MERCHANT_ADMIN',     '/v1/invoices', 'POST'),
('p', 'SALES_REP',          '/v1/invoices', 'POST')
ON CONFLICT DO NOTHING;
