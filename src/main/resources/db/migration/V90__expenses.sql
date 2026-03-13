CREATE TABLE expenses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  distributor_id UUID NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  category VARCHAR(50) NOT NULL,
  amount DECIMAL(15,2) NOT NULL,
  expense_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  reference_number VARCHAR(100),
  receipt_url TEXT,
  payment_method VARCHAR(50),
  paid_at TIMESTAMP,
  gl_entry_id UUID,
  created_by UUID NOT NULL,
  approved_by UUID,
  approved_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_expenses_distributor ON expenses(distributor_id);
CREATE INDEX idx_expenses_status ON expenses(distributor_id, status);
CREATE INDEX idx_expenses_date ON expenses(distributor_id, expense_date);
CREATE INDEX idx_expenses_category ON expenses(distributor_id, category);
