package com.zuqi.api.dto.kyc;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantKycRequest {

    @NotBlank(message = "Business name is required")
    private String businessName;

    @NotBlank(message = "Business type is required")
    private String businessType;

    @NotBlank(message = "Physical address is required")
    private String physicalAddress;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "County is required")
    private String county;

    private String kraPin;

    private String nationalIdNumber;
}
