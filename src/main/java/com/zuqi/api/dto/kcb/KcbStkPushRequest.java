package com.zuqi.api.dto.kcb;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record KcbStkPushRequest(

        @NotBlank String phone,

        @NotNull @DecimalMin("1") BigDecimal amount,

        @NotBlank String referenceId,

        @NotBlank String referenceType
) {}
