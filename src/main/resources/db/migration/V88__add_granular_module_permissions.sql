-- Split GENERAL_LEDGER into fine-grained modules and add dedicated modules for
-- Branches, POS, Stock Transfers and Stock Takes so every feature area has its
-- own toggleable permission module in the role editor.
--
-- Existing permissions (gl:read/write/delete under GENERAL_LEDGER) are kept for
-- backward compatibility. New per-module permissions are added alongside them.

-- ═══════════════════════════════════════════════════════════════
-- 1. INSERT NEW PERMISSIONS
-- ═══════════════════════════════════════════════════════════════
INSERT INTO permissions (name, description, module) VALUES

    -- CHART_OF_ACCOUNTS  (/v1/gl/accounts)
    ('chart_of_accounts:read',   'View chart of accounts',          'CHART_OF_ACCOUNTS'),
    ('chart_of_accounts:write',  'Create/update GL accounts',       'CHART_OF_ACCOUNTS'),
    ('chart_of_accounts:delete', 'Delete GL accounts',              'CHART_OF_ACCOUNTS'),

    -- JOURNAL_ENTRIES  (/v1/gl/journals)
    ('journal_entries:read',     'View journal entries',            'JOURNAL_ENTRIES'),
    ('journal_entries:write',    'Create/update/approve journals',  'JOURNAL_ENTRIES'),
    ('journal_entries:delete',   'Delete journal entries',          'JOURNAL_ENTRIES'),

    -- GL_PERIODS  (/v1/gl/periods)
    ('gl_periods:read',          'View accounting periods',         'GL_PERIODS'),
    ('gl_periods:write',         'Create/close/lock/reopen periods','GL_PERIODS'),

    -- COST_CENTERS  (/v1/gl/cost-centers)
    ('cost_centers:read',        'View cost centers',               'COST_CENTERS'),
    ('cost_centers:write',       'Create/update cost centers',      'COST_CENTERS'),
    ('cost_centers:delete',      'Delete cost centers',             'COST_CENTERS'),

    -- BUDGETS  (/v1/gl/budgets)
    ('budgets:read',             'View budgets and variance reports','BUDGETS'),
    ('budgets:write',            'Create/update budgets',           'BUDGETS'),

    -- BRANCHES  (/v1/branches)
    ('branches:read',            'View branches',                   'BRANCHES'),
    ('branches:write',           'Create/update branches',          'BRANCHES'),
    ('branches:delete',          'Delete branches',                 'BRANCHES'),

    -- POS  (/v1/pos)
    ('pos:read',                 'View POS terminals, shifts, sales','POS'),
    ('pos:write',                'Process POS sales and shifts',    'POS'),

    -- STOCK_TRANSFERS  (/v1/inventory/transfers)
    ('stock_transfers:read',     'View stock transfers',            'STOCK_TRANSFERS'),
    ('stock_transfers:write',    'Create/approve stock transfers',  'STOCK_TRANSFERS'),
    ('stock_transfers:delete',   'Delete stock transfers',          'STOCK_TRANSFERS'),

    -- STOCK_TAKES  (/v1/inventory/stock-takes)
    ('stock_takes:read',         'View stock take batches',         'STOCK_TAKES'),
    ('stock_takes:write',        'Create/update stock takes',       'STOCK_TAKES'),
    ('stock_takes:delete',       'Delete stock takes',              'STOCK_TAKES')

ON CONFLICT DO NOTHING;


-- ═══════════════════════════════════════════════════════════════
-- 2. SUPER_ADMIN — all new permissions
-- ═══════════════════════════════════════════════════════════════
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN'
  AND p.module IN (
    'CHART_OF_ACCOUNTS','JOURNAL_ENTRIES','GL_PERIODS','COST_CENTERS','BUDGETS',
    'BRANCHES','POS','STOCK_TRANSFERS','STOCK_TAKES'
  )
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ═══════════════════════════════════════════════════════════════
-- 3. DISTRIBUTOR_ADMIN — full access to every module
-- ═══════════════════════════════════════════════════════════════
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'DISTRIBUTOR_ADMIN'
  AND p.module IN (
    'CHART_OF_ACCOUNTS','JOURNAL_ENTRIES','GL_PERIODS','COST_CENTERS','BUDGETS',
    'BRANCHES','POS','STOCK_TRANSFERS','STOCK_TAKES'
  )
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ═══════════════════════════════════════════════════════════════
-- 4. MERCHANT_ADMIN — full access (multi-distributor management)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MERCHANT_ADMIN'
  AND p.module IN (
    'CHART_OF_ACCOUNTS','JOURNAL_ENTRIES','GL_PERIODS','COST_CENTERS','BUDGETS',
    'BRANCHES','POS','STOCK_TRANSFERS','STOCK_TAKES'
  )
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ═══════════════════════════════════════════════════════════════
-- 5. FINANCE — read GL + journals write + budgets write; no branch/POS write
-- ═══════════════════════════════════════════════════════════════
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'FINANCE'
  AND p.name IN (
    'chart_of_accounts:read',
    'journal_entries:read', 'journal_entries:write',
    'gl_periods:read',
    'cost_centers:read',
    'budgets:read', 'budgets:write',
    'branches:read',
    'stock_transfers:read',
    'stock_takes:read'
  )
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ═══════════════════════════════════════════════════════════════
-- 6. WAREHOUSE_MANAGER — stock transfers + stock takes (full) + read branches
-- ═══════════════════════════════════════════════════════════════
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'WAREHOUSE_MANAGER'
  AND p.name IN (
    'branches:read',
    'stock_transfers:read', 'stock_transfers:write',
    'stock_takes:read',     'stock_takes:write'
  )
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);


-- ═══════════════════════════════════════════════════════════════
-- 7. SALES_REP — POS (process sales), read branches
-- ═══════════════════════════════════════════════════════════════
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SALES_REP'
  AND p.name IN (
    'branches:read',
    'pos:read', 'pos:write'
  )
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
