package com.zuqi.api.dto.procurement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
public class GrnItemDto {

    private UUID productId;

    @NotBlank(message = "Product name is required")
    private String productName;

    private String sku;

    private Integer orderedQuantity;

    @NotNull(message = "Received quantity is required")
    @Min(value = 0, message = "Received quantity cannot be negative")
    private Integer receivedQuantity;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal unitCost;

    private String unitOfMeasure;

    private String notes;

    private LocalDate expiryDate;
}
