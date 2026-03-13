package com.zuqi.api.dto.kcb;

import jakarta.validation.constraints.NotBlank;

public record KcbActivateRequest(

        @NotBlank String businessName,

        @NotBlank String accountNumber,

        String kcbAccountType,

        String businessNo,

        String accountType,

        boolean subscriptionAccount
) {}
