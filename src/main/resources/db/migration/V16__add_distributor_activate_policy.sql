-- Add missing activate endpoint policies for distributors
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'SUPER_ADMIN', '/v1/distributors/:id/activate', '.*')
ON CONFLICT DO NOTHING;
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES ('p', 'ADMIN', '/v1/distributors/:id/activate', '.*')
ON CONFLICT DO NOTHING;
