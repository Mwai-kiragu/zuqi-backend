package com.zuqi.api.dto.returns;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PurchaseReturnResponse {
    private UUID id;
    private String returnNumber;
    private UUID distributorId;
    private UUID supplierId;
    private String supplierName;
    private UUID supplierBillId;
    private UUID grnId;
    private String grnNumber;
    private String reason;
    private String status;
    private BigDecimal totalAmount;
    private List<ItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class ItemResponse {
        private UUID id;
        private UUID productId;
        private String productName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalAmount;
    }
}
