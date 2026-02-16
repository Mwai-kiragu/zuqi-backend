package com.zuqi.api.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisableTwoFactorRequest {

    @NotBlank(message = "Current password is required to disable two-factor authentication")
    private String currentPassword;
}
