-- KYC Status endpoint - all roles
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/kyc/status', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/kyc/status', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/kyc/status', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/kyc/status', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/kyc/status', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/kyc/status', 'GET') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DRIVER', '/v1/kyc/status', 'GET') ON CONFLICT DO NOTHING;

-- Merchant KYC submission
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/kyc/merchant', 'POST') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/kyc/merchant', 'POST') ON CONFLICT DO NOTHING;

-- Distributor KYC submission
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/kyc/distributor', 'POST') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/kyc/distributor', 'POST') ON CONFLICT DO NOTHING;

-- File upload - all authenticated roles
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/files/upload', 'POST') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DISTRIBUTOR_ADMIN', '/v1/files/upload', 'POST') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'MERCHANT', '/v1/files/upload', 'POST') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SALES_REP', '/v1/files/upload', 'POST') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'WAREHOUSE_MANAGER', '/v1/files/upload', 'POST') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'FINANCE', '/v1/files/upload', 'POST') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'DRIVER', '/v1/files/upload', 'POST') ON CONFLICT DO NOTHING;

-- Email verification endpoints (public, but add for completeness)
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/auth/verify-email', 'POST') ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/auth/resend-verification-otp', 'POST') ON CONFLICT DO NOTHING;
