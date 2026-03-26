CREATE TABLE approval_thresholds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id UUID REFERENCES distributors(id),
    workflow_type VARCHAR(50) NOT NULL,
    min_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    max_amount NUMERIC(15,2),
    required_approvals INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/approval-thresholds', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/approval-thresholds' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/approval-thresholds', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/approval-thresholds' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/approval-thresholds/.*', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/approval-thresholds/.*' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/approval-thresholds/.*', 'PUT' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/approval-thresholds/.*' AND v2='PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'SUPER_ADMIN', '/v1/approval-thresholds/.*', 'DELETE' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='SUPER_ADMIN' AND v1='/v1/approval-thresholds/.*' AND v2='DELETE');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT_ADMIN', '/v1/approval-thresholds', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/approval-thresholds' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT_ADMIN', '/v1/approval-thresholds', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/approval-thresholds' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT_ADMIN', '/v1/approval-thresholds/.*', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/approval-thresholds/.*' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT_ADMIN', '/v1/approval-thresholds/.*', 'PUT' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/approval-thresholds/.*' AND v2='PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'MERCHANT_ADMIN', '/v1/approval-thresholds/.*', 'DELETE' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='MERCHANT_ADMIN' AND v1='/v1/approval-thresholds/.*' AND v2='DELETE');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/approval-thresholds', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/approval-thresholds' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/approval-thresholds', 'POST' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/approval-thresholds' AND v2='POST');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/approval-thresholds/.*', 'GET' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/approval-thresholds/.*' AND v2='GET');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/approval-thresholds/.*', 'PUT' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/approval-thresholds/.*' AND v2='PUT');
INSERT INTO casbin_rule (ptype, v0, v1, v2) SELECT 'p', 'DISTRIBUTOR_ADMIN', '/v1/approval-thresholds/.*', 'DELETE' WHERE NOT EXISTS (SELECT 1 FROM casbin_rule WHERE ptype='p' AND v0='DISTRIBUTOR_ADMIN' AND v1='/v1/approval-thresholds/.*' AND v2='DELETE');
