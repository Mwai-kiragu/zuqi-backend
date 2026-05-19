package com.zuqi.api.dto.ncba;

import jakarta.validation.constraints.NotBlank;

public record NcbaActivateRequest(

        @NotBlank String businessName,

        @NotBlank String paybillNo,

        String network
) {}
