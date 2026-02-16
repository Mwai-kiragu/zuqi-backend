package com.zuqi.api.dto.user;

import com.zuqi.domain.user.TwoFactorAuth.TwoFactorType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnableTwoFactorRequest {

    @NotNull(message = "Two-factor type is required")
    private TwoFactorType type;

    // For SMS-based 2FA
    private String phoneNumber;
}
