-- Billing / Subscription package tables
CREATE TABLE IF NOT EXISTS distributor_subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  distributor_id UUID NOT NULL UNIQUE REFERENCES distributors(id),
  package_type VARCHAR(20) NOT NULL,   -- FREE_TRIAL | SILVER | GOLD | CUSTOM
  custom_modules TEXT,                  -- JSON array, only used when package_type = CUSTOM
  start_date DATE NOT NULL,
  end_date DATE,                        -- NULL = unlimited
  active BOOLEAN NOT NULL DEFAULT true,
  notes TEXT,
  created_by UUID REFERENCES users(id),
  updated_by UUID REFERENCES users(id),
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ds_distributor ON distributor_subscriptions(distributor_id);
CREATE INDEX IF NOT EXISTS idx_ds_active ON distributor_subscriptions(active);
