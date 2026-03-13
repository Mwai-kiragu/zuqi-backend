-- Stock Take Batches
CREATE TABLE IF NOT EXISTS stock_take_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number VARCHAR(100) UNIQUE,
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    branch_id UUID REFERENCES distributor_branches(id),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes TEXT,
    created_by UUID REFERENCES users(id),
    approved_by UUID REFERENCES users(id),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_take_batches_warehouse ON stock_take_batches(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_stock_take_batches_branch ON stock_take_batches(branch_id);
CREATE INDEX IF NOT EXISTS idx_stock_take_batches_status ON stock_take_batches(status);

-- Stock Take Items
CREATE TABLE IF NOT EXISTS stock_take_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL REFERENCES stock_take_batches(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    system_quantity NUMERIC(15,3),
    counted_quantity NUMERIC(15,3),
    variance NUMERIC(15,3),
    notes TEXT,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_take_items_batch ON stock_take_items(batch_id);
CREATE INDEX IF NOT EXISTS idx_stock_take_items_product ON stock_take_items(product_id);
