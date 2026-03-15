-- Allow all POS-capable roles to access shift reconciliation endpoint
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'SUPER_ADMIN',       '/v1/pos/shifts/*/reconciliation', 'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/shifts/*/reconciliation', 'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/pos/shifts/*/reconciliation', 'GET'),
    ('p', 'WAREHOUSE_MANAGER', '/v1/pos/shifts/*/reconciliation', 'GET'),
    ('p', 'SALES_REP',         '/v1/pos/shifts/*/reconciliation', 'GET')
ON CONFLICT DO NOTHING;
