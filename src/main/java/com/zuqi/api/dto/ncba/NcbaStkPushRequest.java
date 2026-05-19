package com.zuqi.api.dto.ncba;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record NcbaStkPushRequest(

        @NotBlank String phone,

        @NotNull @DecimalMin("1") BigDecimal amount,

        @NotBlank String referenceId,

        @NotBlank String referenceType,

        String accountNo
) {}
