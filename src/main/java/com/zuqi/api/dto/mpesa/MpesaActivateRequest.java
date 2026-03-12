package com.zuqi.api.dto.mpesa;

import com.zuqi.domain.mpesa.MpesaTransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MpesaActivateRequest(

        @NotBlank String businessName,

        @NotBlank String consumerKey,

        @NotBlank String consumerSecret,

        @NotBlank String passKey,

        String businessShortCode,

        @NotNull MpesaTransactionType transactionType,

        String accountReference,

        String storeNumber,

        String businessNo,

        String tillNumber,

        String hoNumber,

        String externalId,

        boolean termsAccepted
) {}
