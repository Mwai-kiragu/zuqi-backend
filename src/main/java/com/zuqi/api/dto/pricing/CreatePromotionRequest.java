package com.zuqi.api.dto.pricing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreatePromotionRequest {
    @NotBlank private String name;
    @NotBlank private String promotionType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private UUID productId;
    private Integer categoryId;
    @NotNull private LocalDate validFrom;
    @NotNull private LocalDate validTo;
}
