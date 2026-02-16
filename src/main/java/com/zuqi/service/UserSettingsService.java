package com.zuqi.service;

import com.zuqi.api.dto.user.NotificationSettingsRequest;
import com.zuqi.api.dto.user.NotificationSettingsResponse;
import com.zuqi.api.dto.user.SecuritySettingsResponse;

import java.util.UUID;

public interface UserSettingsService {

    /**
     * Get notification settings for a user
     */
    NotificationSettingsResponse getNotificationSettings(UUID userId);

    /**
     * Update notification settings for a user
     */
    NotificationSettingsResponse updateNotificationSettings(UUID userId, NotificationSettingsRequest request);

    /**
     * Get security settings for a user
     */
    SecuritySettingsResponse getSecuritySettings(UUID userId);

    /**
     * Create default settings for a new user
     */
    void createDefaultSettings(UUID userId);
}
