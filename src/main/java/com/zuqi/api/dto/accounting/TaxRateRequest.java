package com.zuqi.api.dto.accounting;

import com.zuqi.domain.accounting.TaxType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TaxRateRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String code;
    @NotNull
    private BigDecimal rate;
    private TaxType taxType = TaxType.PERCENTAGE;
    private String appliesTo = "ALL";
    private boolean isCompound = false;
    private boolean isDefault = false;
    private boolean active = true;
    private String description;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
