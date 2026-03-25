-- V160: Maker-checker for stock adjustments and POS shift reconciliation

-- Stock movements: track pending approvals
ALTER TABLE stock_movements
  ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS created_by_id   UUID REFERENCES users(id);

-- POS shifts: track reconciliation approval (cashier ≠ reconciler)
ALTER TABLE pos_shifts
  ADD COLUMN IF NOT EXISTS reconciliation_status  VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED',
  ADD COLUMN IF NOT EXISTS reconciled_by_id       UUID REFERENCES users(id),
  ADD COLUMN IF NOT EXISTS reconciled_at          TIMESTAMP;

-- Casbin: VERIFIER / AUTHORIZER / admins can approve stock adjustments and shift reconciliations
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'VERIFIER',          '/v1/inventory/adjustments/.*/approve', 'POST'),
  ('p', 'AUTHORIZER',        '/v1/inventory/adjustments/.*/approve', 'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/inventory/adjustments/.*/approve', 'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/inventory/adjustments/.*/approve', 'POST'),
  ('p', 'VERIFIER',          '/v1/pos/shifts/.*/reconcile',          'POST'),
  ('p', 'AUTHORIZER',        '/v1/pos/shifts/.*/reconcile',          'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/pos/shifts/.*/reconcile',          'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/pos/shifts/.*/reconcile',          'POST')
ON CONFLICT DO NOTHING;
