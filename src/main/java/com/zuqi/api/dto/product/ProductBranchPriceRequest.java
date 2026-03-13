package com.zuqi.api.dto.product;

import jakarta.validation.constraints.DecimalMin;
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
public class ProductBranchPriceRequest {

    @NotNull(message = "Branch ID is required")
    private UUID branchId;

    /**
     * Branch-specific price override. null means use the product's default unit price.
     */
    @DecimalMin(value = "0.0", message = "Unit price must be non-negative")
    private BigDecimal unitPrice;

    @Builder.Default
    private boolean active = true;
}
