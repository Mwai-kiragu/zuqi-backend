package com.zuqi.api.dto.kyc;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KycRejectRequest {
    @NotBlank(message = "Rejection reason is required")
    private String reason;
}
