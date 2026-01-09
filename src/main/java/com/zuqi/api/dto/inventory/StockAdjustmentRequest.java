package com.zuqi.api.dto.inventory;

import com.zuqi.domain.inventory.StockMovement;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentRequest {

    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Quantity is required")
    private BigDecimal quantity;

    @NotNull(message = "Movement type is required")
    private StockMovement.MovementType movementType;

    private String referenceType;
    private UUID referenceId;
    private String notes;
}
