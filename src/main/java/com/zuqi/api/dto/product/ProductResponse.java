package com.zuqi.api.dto.product;

import com.zuqi.domain.product.Product;
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
public class ProductResponse {

    private UUID id;
    private String sku;
    private String name;
    private String description;
    private Long categoryId;
    private String categoryName;
    private UUID distributorId;
    private String distributorName;
    private String unitOfMeasure;
    private BigDecimal unitPrice;
    private BigDecimal costPrice;
    private String imageUrl;
    private String barcode;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String deactivationReason;
    private LocalDateTime deactivatedAt;
    private String deactivatedByName;

    public static ProductResponse fromEntity(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .distributorId(product.getDistributor() != null ? product.getDistributor().getId() : null)
                .distributorName(product.getDistributor() != null ? product.getDistributor().getName() : null)
                .unitOfMeasure(product.getUnitOfMeasure())
                .unitPrice(product.getUnitPrice())
                .costPrice(product.getCostPrice())
                .imageUrl(product.getImageUrl())
                .barcode(product.getBarcode())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .deactivationReason(product.getDeactivationReason())
                .deactivatedAt(product.getDeactivatedAt())
                .deactivatedByName(product.getDeactivatedBy() != null ? product.getDeactivatedBy().getFullName() : null)
                .build();
    }
}
