package com.zuqi.service;

import com.zuqi.api.dto.user.EnableTwoFactorRequest;
import com.zuqi.api.dto.user.EnableTwoFactorResponse;

import java.util.List;
import java.util.UUID;

public interface TwoFactorAuthService {

    /**
     * Initialize 2FA setup for a user (generates secret key, QR code, backup codes)
     */
    EnableTwoFactorResponse initializeTwoFactor(UUID userId, EnableTwoFactorRequest request);

    /**
     * Verify and enable 2FA after user confirms with a valid code
     */
    boolean verifyAndEnable(UUID userId, String code);

    /**
     * Disable 2FA for a user
     */
    void disableTwoFactor(UUID userId, String currentPassword);

    /**
     * Verify a 2FA code during login
     */
    boolean verifyCode(UUID userId, String code);

    /**
     * Verify a backup code during login
     */
    boolean verifyBackupCode(UUID userId, String code);

    /**
     * Generate new backup codes for a user
     */
    List<String> regenerateBackupCodes(UUID userId);

    /**
     * Check if user has 2FA enabled
     */
    boolean isTwoFactorEnabled(UUID userId);
}
