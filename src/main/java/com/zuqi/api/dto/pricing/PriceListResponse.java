package com.zuqi.api.dto.pricing;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class PriceListResponse {
    private UUID id;
    private UUID distributorId;
    private String name;
    private String description;
    private boolean isDefault;
    private boolean active;
    private LocalDate validFrom;
    private LocalDate validTo;
    private List<ItemResponse> items;
    private LocalDateTime createdAt;

    @Data @Builder
    public static class ItemResponse {
        private UUID id;
        private UUID productId;
        private String productName;
        private BigDecimal unitPrice;
        private BigDecimal discountPercent;
    }
}
