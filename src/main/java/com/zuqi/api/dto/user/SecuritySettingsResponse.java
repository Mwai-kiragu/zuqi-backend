package com.zuqi.api.dto.user;

import com.zuqi.domain.user.TwoFactorAuth;
import com.zuqi.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecuritySettingsResponse {

    private boolean twoFactorEnabled;
    private String twoFactorType;
    private LocalDateTime twoFactorEnabledAt;
    private Integer backupCodesRemaining;
    private LocalDateTime passwordChangedAt;
    private Long passwordAgeDays;
    private boolean emailVerified;
    private boolean phoneVerified;
    private LocalDateTime lastLoginAt;

    public static SecuritySettingsResponse fromUserAndTwoFactor(User user, TwoFactorAuth twoFactorAuth) {
        SecuritySettingsResponseBuilder builder = SecuritySettingsResponse.builder()
                .twoFactorEnabled(user.isTwoFactorEnabled())
                .passwordChangedAt(user.getPasswordChangedAt())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .lastLoginAt(user.getLastLoginAt());

        // Calculate password age
        if (user.getPasswordChangedAt() != null) {
            builder.passwordAgeDays(ChronoUnit.DAYS.between(user.getPasswordChangedAt(), LocalDateTime.now()));
        } else if (user.getCreatedAt() != null) {
            builder.passwordAgeDays(ChronoUnit.DAYS.between(user.getCreatedAt(), LocalDateTime.now()));
        }

        // Add 2FA details if available
        if (twoFactorAuth != null && twoFactorAuth.isVerified()) {
            builder.twoFactorType(twoFactorAuth.getType().name())
                    .twoFactorEnabledAt(twoFactorAuth.getCreatedAt())
                    .backupCodesRemaining(twoFactorAuth.getBackupCodesRemaining());
        }

        return builder.build();
    }
}
