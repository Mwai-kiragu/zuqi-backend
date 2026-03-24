-- ===========================================================================================
-- V152__ncba_integration.sql
-- NCBA bank integration config and transaction tracking
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS ncba_configs (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  distributor_id UUID NOT NULL REFERENCES distributors(id),
  client_id      VARCHAR(255),
  client_secret  VARCHAR(255),
  account_number VARCHAR(50),
  environment    VARCHAR(20) NOT NULL DEFAULT 'SANDBOX',
  status         VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
  created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ncba_configs_distributor ON ncba_configs(distributor_id);

CREATE TABLE IF NOT EXISTS ncba_transactions (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  distributor_id        UUID NOT NULL REFERENCES distributors(id),
  transaction_reference VARCHAR(100) UNIQUE,
  transaction_type      VARCHAR(30),
  amount                NUMERIC(15,2),
  account_number        VARCHAR(50),
  status                VARCHAR(20),
  request_payload       JSONB,
  response_payload      JSONB,
  created_at            TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ncba_transactions_distributor ON ncba_transactions(distributor_id);
CREATE INDEX IF NOT EXISTS idx_ncba_transactions_status      ON ncba_transactions(status);

-- Casbin policies
INSERT INTO casbin_rule (ptype, v0, v1, v2) VALUES
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/ncba',          'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/ncba',          'POST'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/ncba/.*',       'GET'),
  ('p', 'DISTRIBUTOR_ADMIN', '/v1/ncba/.*',       'POST'),
  ('p', 'MERCHANT_ADMIN',    '/v1/ncba',          'GET'),
  ('p', 'MERCHANT_ADMIN',    '/v1/ncba/.*',       'GET'),
  ('p', 'FINANCE',           '/v1/ncba',          'GET'),
  ('p', 'FINANCE',           '/v1/ncba/.*',       'GET'),
  ('p', 'FINANCE',           '/v1/ncba/.*',       'POST'),
  ('p', 'SUPER_ADMIN',       '/v1/ncba',          'GET'),
  ('p', 'SUPER_ADMIN',       '/v1/ncba/.*',       'GET')
ON CONFLICT DO NOTHING;
