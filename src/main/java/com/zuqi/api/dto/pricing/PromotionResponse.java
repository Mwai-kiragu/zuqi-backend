package com.zuqi.api.dto.pricing;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class PromotionResponse {
    private UUID id;
    private UUID distributorId;
    private String name;
    private String promotionType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private UUID productId;
    private String productName;
    private Integer categoryId;
    private LocalDate validFrom;
    private LocalDate validTo;
    private boolean active;
    private LocalDateTime createdAt;
}
