package com.zuqi.ai.synthetic.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * In-memory representation of a synthetic inventory movement.
 *
 * Mirrors {@link com.zuqi.domain.inventory.StockMovement} fields relevant
 * for inventory shrinkage detection feature computation.
 *
 * @param syntheticId      UUID for cross-referencing
 * @param warehouseId      Target warehouse UUID (real warehouse from distributor's setup)
 * @param skuId            Product UUID
 * @param movementType     IN, OUT, ADJUSTMENT, TRANSFER
 * @param quantity         Movement quantity (positive)
 * @param previousStock    Stock level before this movement
 * @param newStock         Stock level after this movement
 * @param timestamp        When the movement occurred
 * @param userId           User who recorded it (real user UUID or synthetic)
 * @param isShrinkage      TRUE if this movement was generated as a shrinkage event
 * @param shrinkagePattern Pattern type if isShrinkage=true, e.g. "GRADUAL", "SUDDEN"; null otherwise
 */
public record SyntheticInventoryMovement(
        UUID syntheticId,
        UUID warehouseId,
        UUID skuId,
        String movementType,
        BigDecimal quantity,
        BigDecimal previousStock,
        BigDecimal newStock,
        LocalDateTime timestamp,
        UUID userId,
        boolean isShrinkage,
        String shrinkagePattern
) {}
