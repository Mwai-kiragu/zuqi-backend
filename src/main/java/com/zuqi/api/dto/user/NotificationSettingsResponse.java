package com.zuqi.api.dto.user;

import com.zuqi.domain.user.UserSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsResponse {

    private UUID id;
    private boolean emailNotificationsEnabled;
    private boolean pushNotificationsEnabled;
    private boolean orderUpdatesEnabled;
    private boolean paymentAlertsEnabled;
    private boolean inventoryAlertsEnabled;
    private boolean marketingEmailsEnabled;
    private boolean systemAnnouncementsEnabled;
    private LocalDateTime updatedAt;

    public static NotificationSettingsResponse fromEntity(UserSettings settings) {
        return NotificationSettingsResponse.builder()
                .id(settings.getId())
                .emailNotificationsEnabled(settings.isEmailNotificationsEnabled())
                .pushNotificationsEnabled(settings.isPushNotificationsEnabled())
                .orderUpdatesEnabled(settings.isOrderUpdatesEnabled())
                .paymentAlertsEnabled(settings.isPaymentAlertsEnabled())
                .inventoryAlertsEnabled(settings.isInventoryAlertsEnabled())
                .marketingEmailsEnabled(settings.isMarketingEmailsEnabled())
                .systemAnnouncementsEnabled(settings.isSystemAnnouncementsEnabled())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }

    public static NotificationSettingsResponse defaultSettings() {
        return NotificationSettingsResponse.builder()
                .emailNotificationsEnabled(true)
                .pushNotificationsEnabled(true)
                .orderUpdatesEnabled(true)
                .paymentAlertsEnabled(true)
                .inventoryAlertsEnabled(true)
                .marketingEmailsEnabled(false)
                .systemAnnouncementsEnabled(true)
                .build();
    }
}
