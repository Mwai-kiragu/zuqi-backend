package com.zuqi.api.dto.procurement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementItemDto {

    private UUID productId;

    @NotBlank(message = "Product name is required")
    private String productName;

    private String sku;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private Integer receivedQuantity;

    private BigDecimal estimatedUnitCost;

    private BigDecimal unitCost;

    @NotBlank(message = "Unit of measure is required")
    private String unitOfMeasure;

    private String notes;
}
