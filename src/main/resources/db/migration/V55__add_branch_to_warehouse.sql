-- Add branch_id FK to warehouses
ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS branch_id UUID REFERENCES distributor_branches(id);

CREATE INDEX IF NOT EXISTS idx_warehouses_branch ON warehouses(branch_id);
