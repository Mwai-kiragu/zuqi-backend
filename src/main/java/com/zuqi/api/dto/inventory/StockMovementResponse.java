package com.zuqi.api.dto.inventory;

import com.zuqi.domain.inventory.StockMovement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {

    private UUID id;
    private UUID warehouseId;
    private String warehouseName;
    private UUID productId;
    private String productName;
    private String productSku;
    private StockMovement.MovementType movementType;
    private BigDecimal quantity;
    private String referenceType;
    private UUID referenceId;
    private String notes;
    private UUID createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    /** APPROVED | PENDING_APPROVAL | REJECTED */
    private String approvalStatus;
}
