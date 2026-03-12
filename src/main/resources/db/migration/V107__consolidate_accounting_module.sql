-- Consolidate bankReconciliation and taxRates into a single 'accounting' module

-- 1. Add the new accounting module
INSERT INTO billing_modules (module_key, display_name, description, sort_order) VALUES
  ('accounting', 'Accounting', 'Chart of accounts, journal entries, bank reconciliation, tax rates, periods, cost centres and budgets', 21)
ON CONFLICT (module_key) DO NOTHING;

-- 2. Deactivate the old separate modules
UPDATE billing_modules SET active = false WHERE module_key IN ('bankReconciliation', 'taxRates');

-- 3. Update GOLD package: replace bankReconciliation + taxRates with accounting
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","customers","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","gl","ai","credit","pos","branches","stockTransfers","stockTakes","accounting","expenses","fundsTransfer","warehouses","paymentSetup"]'
WHERE name = 'GOLD';

-- 4. Update SILVER package: replace bankReconciliation + taxRates with accounting
UPDATE billing_packages
SET modules = '["dashboard","orders","merchants","customers","products","inventory","payments","invoices","suppliers","procurement","reports","approvals","pos","branches","stockTransfers","stockTakes","accounting","expenses","fundsTransfer","warehouses","paymentSetup"]'
WHERE name = 'SILVER';
