package com.zuqi.api.dto.accounting;

import com.zuqi.domain.accounting.TaxRate;
import com.zuqi.domain.accounting.TaxType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TaxRateResponse {
    private UUID id;
    private String name;
    private String code;
    private BigDecimal rate;
    private TaxType taxType;
    private String appliesTo;
    private boolean isCompound;
    private boolean isDefault;
    private boolean active;
    private String description;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private LocalDateTime createdAt;

    public static TaxRateResponse from(TaxRate t) {
        return TaxRateResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .code(t.getCode())
                .rate(t.getRate())
                .taxType(t.getTaxType())
                .appliesTo(t.getAppliesTo())
                .isCompound(t.isCompound())
                .isDefault(t.isDefault())
                .active(t.isActive())
                .description(t.getDescription())
                .effectiveFrom(t.getEffectiveFrom())
                .effectiveTo(t.getEffectiveTo())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
