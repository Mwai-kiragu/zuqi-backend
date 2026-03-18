-- ===========================================================================================
-- V135__create_product_batches.sql
-- Part of Phase 2 — Product Batch Inventory Tracking
-- Author: Zuqi Engineering
-- Date: 2026-03-18
-- ===========================================================================================

CREATE TABLE IF NOT EXISTS product_batches (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id    UUID NOT NULL,
    warehouse_id      UUID NOT NULL,
    product_id        UUID NOT NULL,
    batch_number      VARCHAR(100) NOT NULL,
    manufacture_date  DATE,
    expiry_date       DATE,
    initial_quantity  DOUBLE PRECISION NOT NULL DEFAULT 0,
    current_quantity  DOUBLE PRECISION NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP DEFAULT NOW(),
    updated_at        TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_product_batches_distributor FOREIGN KEY (distributor_id) REFERENCES distributors(id),
    CONSTRAINT fk_product_batches_warehouse   FOREIGN KEY (warehouse_id)   REFERENCES warehouses(id),
    CONSTRAINT fk_product_batches_product     FOREIGN KEY (product_id)     REFERENCES products(id),
    CONSTRAINT uq_product_batches_warehouse_product_batch UNIQUE (warehouse_id, product_id, batch_number)
);

COMMENT ON TABLE product_batches IS 'Product inventory batches with expiry tracking for warehouse management';
COMMENT ON COLUMN product_batches.batch_number      IS 'Manufacturer or internal batch/lot number';
COMMENT ON COLUMN product_batches.initial_quantity  IS 'Quantity at the time the batch was received';
COMMENT ON COLUMN product_batches.current_quantity  IS 'Remaining quantity after adjustments and sales';
COMMENT ON COLUMN product_batches.status            IS 'Lifecycle status: ACTIVE, DEPLETED, EXPIRED, QUARANTINED';

CREATE INDEX IF NOT EXISTS idx_product_batches_warehouse_expiry
    ON product_batches (warehouse_id, expiry_date);

CREATE INDEX IF NOT EXISTS idx_product_batches_product_expiry
    ON product_batches (product_id, expiry_date);

CREATE INDEX IF NOT EXISTS idx_product_batches_distributor_status
    ON product_batches (distributor_id, status);

CREATE INDEX IF NOT EXISTS idx_product_batches_expiry_date
    ON product_batches (expiry_date)
    WHERE expiry_date IS NOT NULL;
