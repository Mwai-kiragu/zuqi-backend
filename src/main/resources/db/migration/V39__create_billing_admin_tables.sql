-- Billing admin tables: modules and package definitions

CREATE TABLE IF NOT EXISTS billing_modules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  module_key VARCHAR(50) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  description TEXT,
  active BOOLEAN NOT NULL DEFAULT true,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS billing_packages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(50) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  description TEXT,
  is_system BOOLEAN NOT NULL DEFAULT false,
  modules TEXT NOT NULL DEFAULT '[]',
  active BOOLEAN NOT NULL DEFAULT true,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

-- Seed 16 modules
INSERT INTO billing_modules (module_key, display_name, description, sort_order) VALUES
('dashboard',   'Dashboard',       'Main overview dashboard',                    1),
('orders',      'Orders',          'Order management',                           2),
('merchants',   'Merchants',       'Merchant/customer management',               3),
('products',    'Products',        'Product catalogue management',               4),
('inventory',   'Inventory',       'Stock and warehouse management',             5),
('payments',    'Payments',        'Payment recording and reconciliation',       6),
('invoices',    'Invoices',        'Invoice generation and management',          7),
('suppliers',   'Suppliers',       'Supplier management',                        8),
('procurement', 'Procurement',     'Purchase requisitions and orders',           9),
('reports',     'Reports',         'Business reporting and analytics',          10),
('approvals',   'Approvals',       'Workflow approval management',              11),
('credit',      'Credit',          'Merchant credit scoring and limits',        12),
('gl',          'General Ledger',  'General ledger and accounting entries',     13),
('ai',          'AI Features',     'AI-powered insights and recommendations',   14),
('users',       'Users',           'User and role management',                  15),
('audit',       'Audit Logs',      'System activity audit trail',               16)
ON CONFLICT (module_key) DO NOTHING;

-- Seed 4 system packages
INSERT INTO billing_packages (name, display_name, description, is_system, modules, sort_order) VALUES
(
  'FREE_TRIAL',
  'Free Trial',
  '30-day trial with core modules',
  true,
  '["dashboard","orders","merchants","products","inventory"]',
  1
),
(
  'SILVER',
  'Silver',
  'Full distributor operations suite',
  true,
  '["dashboard","orders","merchants","products","inventory","payments","invoices","suppliers","procurement","reports","approvals"]',
  2
),
(
  'GOLD',
  'Gold',
  'Silver + General Ledger, AI, and Credit modules',
  true,
  '["dashboard","orders","merchants","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","gl","ai","credit"]',
  3
),
(
  'CUSTOM',
  'Custom',
  'Manually select any module combination',
  true,
  '[]',
  4
)
ON CONFLICT (name) DO NOTHING;
