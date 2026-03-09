package com.zuqi.api.dto.product;

import com.zuqi.domain.product.ProductBranchPrice;
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
public class ProductBranchPriceResponse {

    private UUID branchId;
    private String branchName;

    /**
     * Branch-specific price override. null means the product's default unit price applies.
     */
    private BigDecimal unitPrice;

    private boolean active;

    public static ProductBranchPriceResponse fromEntity(ProductBranchPrice entity) {
        return ProductBranchPriceResponse.builder()
                .branchId(entity.getBranch().getId())
                .branchName(entity.getBranch().getName())
                .unitPrice(entity.getUnitPrice())
                .active(entity.isActive())
                .build();
    }
}
