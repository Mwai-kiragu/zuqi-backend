package com.zuqi.service.impl;

import com.zuqi.api.dto.user.NotificationSettingsRequest;
import com.zuqi.api.dto.user.NotificationSettingsResponse;
import com.zuqi.api.dto.user.SecuritySettingsResponse;
import com.zuqi.domain.user.TwoFactorAuth;
import com.zuqi.domain.user.User;
import com.zuqi.domain.user.UserSettings;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.TwoFactorAuthRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.repository.UserSettingsRepository;
import com.zuqi.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSettingsServiceImpl implements UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public NotificationSettingsResponse getNotificationSettings(UUID userId) {
        log.info("Fetching notification settings for user: {}", userId);

        return userSettingsRepository.findByUserId(userId)
                .map(NotificationSettingsResponse::fromEntity)
                .orElseGet(() -> {
                    log.info("No settings found for user {}, returning defaults", userId);
                    return NotificationSettingsResponse.defaultSettings();
                });
    }

    @Override
    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(UUID userId, NotificationSettingsRequest request) {
        log.info("Updating notification settings for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("Creating new settings for user: {}", userId);
                    return UserSettings.builder()
                            .user(user)
                            .build();
                });

        // Update only non-null fields from request
        if (request.getEmailNotificationsEnabled() != null) {
            settings.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        }
        if (request.getPushNotificationsEnabled() != null) {
            settings.setPushNotificationsEnabled(request.getPushNotificationsEnabled());
        }
        if (request.getOrderUpdatesEnabled() != null) {
            settings.setOrderUpdatesEnabled(request.getOrderUpdatesEnabled());
        }
        if (request.getPaymentAlertsEnabled() != null) {
            settings.setPaymentAlertsEnabled(request.getPaymentAlertsEnabled());
        }
        if (request.getInventoryAlertsEnabled() != null) {
            settings.setInventoryAlertsEnabled(request.getInventoryAlertsEnabled());
        }
        if (request.getMarketingEmailsEnabled() != null) {
            settings.setMarketingEmailsEnabled(request.getMarketingEmailsEnabled());
        }
        if (request.getSystemAnnouncementsEnabled() != null) {
            settings.setSystemAnnouncementsEnabled(request.getSystemAnnouncementsEnabled());
        }

        UserSettings savedSettings = userSettingsRepository.save(settings);
        log.info("Notification settings updated for user: {}", userId);

        return NotificationSettingsResponse.fromEntity(savedSettings);
    }

    @Override
    @Transactional(readOnly = true)
    public SecuritySettingsResponse getSecuritySettings(UUID userId) {
        log.info("Fetching security settings for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId).orElse(null);

        return SecuritySettingsResponse.fromUserAndTwoFactor(user, twoFactorAuth);
    }

    @Override
    @Transactional
    public void createDefaultSettings(UUID userId) {
        log.info("Creating default settings for user: {}", userId);

        if (userSettingsRepository.existsByUserId(userId)) {
            log.info("Settings already exist for user: {}", userId);
            return;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        UserSettings settings = UserSettings.builder()
                .user(user)
                .emailNotificationsEnabled(true)
                .pushNotificationsEnabled(true)
                .orderUpdatesEnabled(true)
                .paymentAlertsEnabled(true)
                .inventoryAlertsEnabled(true)
                .marketingEmailsEnabled(false)
                .systemAnnouncementsEnabled(true)
                .build();

        userSettingsRepository.save(settings);
        log.info("Default settings created for user: {}", userId);
    }
}
