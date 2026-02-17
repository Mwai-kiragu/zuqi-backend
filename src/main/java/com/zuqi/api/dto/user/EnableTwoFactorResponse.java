package com.zuqi.api.dto.user;

import com.zuqi.domain.user.TwoFactorAuth.TwoFactorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnableTwoFactorResponse {

    private TwoFactorType type;

    // For TOTP - the secret key to display as QR code
    private String secretKey;

    // For TOTP - the QR code URL (otpauth:// format)
    private String qrCodeUrl;

    // Backup codes (only shown once during setup)
    private List<String> backupCodes;

    // Message to display to user
    private String message;
}
