-- Casbin policies for accounting & financial report endpoints added in accounting module
-- SUPER_ADMIN bypasses Casbin entirely; these cover DISTRIBUTOR_ADMIN, MERCHANT_ADMIN, FINANCE

-- ─────────────────────────────────────────────────────────────
-- GL Financial Reports (general-ledger, balance-sheet, profit-loss, cash-flow)
-- ─────────────────────────────────────────────────────────────
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/reports/general-ledger', 'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/reports/balance-sheet',  'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/reports/profit-loss',    'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/gl/reports/cash-flow',      'GET'),

    ('p', 'MERCHANT_ADMIN',    '/v1/gl/reports/general-ledger', 'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/gl/reports/balance-sheet',  'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/gl/reports/profit-loss',    'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/gl/reports/cash-flow',      'GET'),

    ('p', 'FINANCE',           '/v1/gl/reports/general-ledger', 'GET'),
    ('p', 'FINANCE',           '/v1/gl/reports/balance-sheet',  'GET'),
    ('p', 'FINANCE',           '/v1/gl/reports/profit-loss',    'GET'),
    ('p', 'FINANCE',           '/v1/gl/reports/cash-flow',      'GET')

ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────
-- AR / AP Aging Reports
-- ─────────────────────────────────────────────────────────────
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/reports/aging/ar', 'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/reports/aging/ap', 'GET'),

    ('p', 'MERCHANT_ADMIN',    '/v1/reports/aging/ar', 'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/reports/aging/ap', 'GET'),

    ('p', 'FINANCE',           '/v1/reports/aging/ar', 'GET'),
    ('p', 'FINANCE',           '/v1/reports/aging/ap', 'GET')

ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────
-- Bank Reconciliations  (/v1/accounting/bank-reconciliations)
-- ─────────────────────────────────────────────────────────────
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    -- DISTRIBUTOR_ADMIN: full CRUD + reconcile action
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/bank-reconciliations',           'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/bank-reconciliations',           'POST'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/bank-reconciliations/:id',       'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/bank-reconciliations/:id',       'PUT'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/bank-reconciliations/:id',       'DELETE'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/bank-reconciliations/:id/reconcile', 'POST'),

    -- MERCHANT_ADMIN: full CRUD + reconcile action (multi-distributor view)
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/bank-reconciliations',           'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/bank-reconciliations',           'POST'),
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/bank-reconciliations/:id',       'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/bank-reconciliations/:id',       'PUT'),
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/bank-reconciliations/:id',       'DELETE'),
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/bank-reconciliations/:id/reconcile', 'POST'),

    -- FINANCE: read + create + reconcile, no delete
    ('p', 'FINANCE',           '/v1/accounting/bank-reconciliations',           'GET'),
    ('p', 'FINANCE',           '/v1/accounting/bank-reconciliations',           'POST'),
    ('p', 'FINANCE',           '/v1/accounting/bank-reconciliations/:id',       'GET'),
    ('p', 'FINANCE',           '/v1/accounting/bank-reconciliations/:id',       'PUT'),
    ('p', 'FINANCE',           '/v1/accounting/bank-reconciliations/:id/reconcile', 'POST')

ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────
-- Tax Rates  (/v1/accounting/tax-rates)
-- ─────────────────────────────────────────────────────────────
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
    -- DISTRIBUTOR_ADMIN: full CRUD
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/tax-rates',         'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/tax-rates',         'POST'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/tax-rates/active',  'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/tax-rates/:id',     'GET'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/tax-rates/:id',     'PUT'),
    ('p', 'DISTRIBUTOR_ADMIN', '/v1/accounting/tax-rates/:id',     'DELETE'),

    -- MERCHANT_ADMIN: full CRUD
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/tax-rates',         'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/tax-rates',         'POST'),
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/tax-rates/active',  'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/tax-rates/:id',     'GET'),
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/tax-rates/:id',     'PUT'),
    ('p', 'MERCHANT_ADMIN',    '/v1/accounting/tax-rates/:id',     'DELETE'),

    -- FINANCE: read-only
    ('p', 'FINANCE',           '/v1/accounting/tax-rates',         'GET'),
    ('p', 'FINANCE',           '/v1/accounting/tax-rates/active',  'GET'),
    ('p', 'FINANCE',           '/v1/accounting/tax-rates/:id',     'GET'),

    -- SALES_REP: active list only (needed when applying tax on invoices/POS)
    ('p', 'SALES_REP',         '/v1/accounting/tax-rates/active',  'GET')

ON CONFLICT DO NOTHING;
