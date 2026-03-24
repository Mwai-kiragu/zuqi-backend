package com.zuqi.api.dto.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ProductBatchRequest {
    @NotNull private UUID warehouseId;
    @NotNull private UUID productId;
    @NotBlank private String batchNumber;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;
    @NotNull @Positive private Double initialQuantity;
}
