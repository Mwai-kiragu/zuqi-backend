-- Add preferred supplier to purchase_requisitions (optional, requestor preference)
ALTER TABLE purchase_requisitions
  ADD COLUMN IF NOT EXISTS preferred_supplier_id   UUID REFERENCES suppliers(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS preferred_supplier_name VARCHAR(255);
