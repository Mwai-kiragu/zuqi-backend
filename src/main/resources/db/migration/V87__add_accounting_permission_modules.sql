-- Add accounting & financial report modules as first-class permission modules
-- so they appear in the role editor (EditRolePage) and can be toggled per role.

-- ─────────────────────────────────────────────────────────────
-- 1. Insert new permissions
-- ─────────────────────────────────────────────────────────────
INSERT INTO permissions (name, description, module) VALUES
    -- GL REPORTS (general-ledger, balance-sheet, profit-loss, cash-flow)
    ('gl_reports:read',  'View GL financial reports (GL, Balance Sheet, P&L, Cash Flow)', 'GL_REPORTS'),

    -- AR AGING
    ('ar_aging:read',    'View accounts receivable aging report', 'AR_AGING'),

    -- AP AGING
    ('ap_aging:read',    'View accounts payable aging report',    'AP_AGING'),

    -- BANK RECONCILIATION
    ('bank_reconciliation:read',   'View bank reconciliations',                  'BANK_RECONCILIATION'),
    ('bank_reconciliation:write',  'Create, update and reconcile bank records',  'BANK_RECONCILIATION'),
    ('bank_reconciliation:delete', 'Delete bank reconciliations',                'BANK_RECONCILIATION'),

    -- TAX RATES
    ('tax_rates:read',   'View tax rates',           'TAX_RATES'),
    ('tax_rates:write',  'Create/update tax rates',  'TAX_RATES'),
    ('tax_rates:delete', 'Delete tax rates',         'TAX_RATES')

ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────
-- 2. SUPER_ADMIN: gets all new permissions
-- ─────────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN'
  AND p.module IN ('GL_REPORTS', 'AR_AGING', 'AP_AGING', 'BANK_RECONCILIATION', 'TAX_RATES')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ─────────────────────────────────────────────────────────────
-- 3. DISTRIBUTOR_ADMIN: full access to all accounting modules
-- ─────────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'DISTRIBUTOR_ADMIN'
  AND p.module IN ('GL_REPORTS', 'AR_AGING', 'AP_AGING', 'BANK_RECONCILIATION', 'TAX_RATES')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ─────────────────────────────────────────────────────────────
-- 4. MERCHANT_ADMIN: full access (manages across distributors)
-- ─────────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MERCHANT_ADMIN'
  AND p.module IN ('GL_REPORTS', 'AR_AGING', 'AP_AGING', 'BANK_RECONCILIATION', 'TAX_RATES')
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ─────────────────────────────────────────────────────────────
-- 5. FINANCE: read + write on bank rec; read-only everything else
-- ─────────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'FINANCE'
  AND p.name IN (
    'gl_reports:read',
    'ar_aging:read',
    'ap_aging:read',
    'bank_reconciliation:read',
    'bank_reconciliation:write',
    'tax_rates:read'
  )
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- ─────────────────────────────────────────────────────────────
-- 6. SALES_REP: tax rates read-only (needed when raising invoices)
-- ─────────────────────────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SALES_REP'
  AND p.name = 'tax_rates:read'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
