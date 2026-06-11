-- V232: Casbin policies for POS refund approval workflow.
-- DISTRIBUTOR_ADMIN and FINANCE can approve/reject POS refund approval requests.
-- CASHIER and SALES_REP routes are handled in code (currentUserRequiresApprovalFor).

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/approvals/.*', 'POST'
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/approvals/.*' AND v2='POST'
);

INSERT INTO casbin_rule (ptype, v0, v1, v2)
SELECT 'p', 'FINANCE', '/v1/approvals/.*', 'POST'
WHERE NOT EXISTS (
    SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='FINANCE' AND v1='/v1/approvals/.*' AND v2='POST'
);
