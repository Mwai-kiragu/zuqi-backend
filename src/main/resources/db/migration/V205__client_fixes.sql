-- V201: Client bug fixes

-- Issue 4: payment method visibility — WAREHOUSE_MANAGER + SALES_REP can read M-Pesa and KCB configs
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p','WAREHOUSE_MANAGER','/v1/mpesa','GET'),
  ('p','WAREHOUSE_MANAGER','/v1/mpesa/.*','GET'),
  ('p','WAREHOUSE_MANAGER','/v1/kcb','GET'),
  ('p','WAREHOUSE_MANAGER','/v1/kcb/.*','GET'),
  ('p','SALES_REP','/v1/mpesa','GET'),
  ('p','SALES_REP','/v1/mpesa/.*','GET'),
  ('p','SALES_REP','/v1/kcb','GET'),
  ('p','SALES_REP','/v1/kcb/.*','GET')
ON CONFLICT DO NOTHING;

-- Issue 5: sales return → invoice FK
ALTER TABLE sales_returns ADD COLUMN IF NOT EXISTS invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_sales_returns_invoice ON sales_returns(invoice_id);

-- Issue 6: returns approval workflow — VERIFIER/AUTHORIZER can PUT (approve/reject) returns
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p','VERIFIER','/v1/returns/.*','PUT'),
  ('p','AUTHORIZER','/v1/returns/.*','PUT')
ON CONFLICT DO NOTHING;
