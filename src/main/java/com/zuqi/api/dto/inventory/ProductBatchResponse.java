package com.zuqi.api.dto.inventory;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProductBatchResponse {
    private UUID id;
    private UUID distributorId;
    private UUID warehouseId;
    private String warehouseName;
    private UUID productId;
    private String productName;
    private String batchNumber;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    private Double initialQuantity;
    private Double currentQuantity;
    private String status;
    private LocalDateTime createdAt;
    private boolean expiringSoon; // within 30 days
}
