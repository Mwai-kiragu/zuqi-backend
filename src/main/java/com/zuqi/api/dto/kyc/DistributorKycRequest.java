package com.zuqi.api.dto.kyc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistributorKycRequest {

    private String kraPin;

    private String county;
}
