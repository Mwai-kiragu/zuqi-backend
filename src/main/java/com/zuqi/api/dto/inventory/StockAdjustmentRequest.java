package com.zuqi.api.dto.inventory;

import com.zuqi.domain.inventory.StockMovement;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Optional: batch number for this stock-in (auto-generated if omitted but expiryDate is set). */
    private String batchNumber;

    /** Optional: expiry date for this stock-in. When provided, a ProductBatch record is auto-created. */
    private LocalDate expiryDate;

    /** Optional: set/update the reorder level for this stock record at the same time as the adjustment. */
    private BigDecimal reorderLevel;
}
