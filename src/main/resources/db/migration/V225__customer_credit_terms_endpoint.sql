-- PATCH /v1/customers/{id}/credit-terms — set credit limit + payment terms during approval
-- Required by the approvals flow: approvers edit these fields before approving CUSTOMER_KYC requests

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', r, '/v1/customers/.*/credit-terms', 'PATCH'
FROM (VALUES
  ('SUPER_ADMIN'), ('MERCHANT_ADMIN'), ('DISTRIBUTOR_ADMIN'),
  ('FINANCE'), ('VERIFIER'), ('AUTHORIZER')
) AS t(r)
WHERE NOT EXISTS (
  SELECT 1 FROM casbin_rule
  WHERE ptype = 'p' AND v0 = t.r AND v1 = '/v1/customers/.*/credit-terms' AND v2 = 'PATCH'
);
