package com.zuqi.api.dto.product;

import com.zuqi.domain.product.ProductCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for product category data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryResponse {

    private Long id;
    private String name;
    private String description;
    private Long parentId;
    private String parentName;
    private UUID distributorId;

    /**
     * Converts a ProductCategory entity to ProductCategoryResponse DTO.
     */
    public static ProductCategoryResponse fromEntity(ProductCategory category) {
        return ProductCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .parentName(category.getParent() != null ? category.getParent().getName() : null)
                .distributorId(category.getDistributor() != null ? category.getDistributor().getId() : null)
                .build();
    }
}
