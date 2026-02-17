package com.zuqi.api.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsRequest {

    private Boolean emailNotificationsEnabled;
    private Boolean pushNotificationsEnabled;
    private Boolean orderUpdatesEnabled;
    private Boolean paymentAlertsEnabled;
    private Boolean inventoryAlertsEnabled;
    private Boolean marketingEmailsEnabled;
    private Boolean systemAnnouncementsEnabled;
}
