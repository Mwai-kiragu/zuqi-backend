package com.zuqi.service;

import com.zuqi.domain.user.User;

public interface EmailService {

    void sendWelcomeEmail(User user, String temporaryPassword);

    void sendPasswordResetEmail(User user, String resetToken);

    void sendPasswordResetOtpEmail(User user, String otp);

    void sendEmailVerificationOtpEmail(User user, String otp);

    void sendPasswordChangedEmail(User user);

    void sendTemplatedEmail(String to, String subject, String templateName, java.util.Map<String, Object> context);

    void sendInvoiceEmailAsync(String to, String subject, java.util.Map<String, Object> context);

    void sendAnomalyAlertEmail(String to, String alertType, String severity, String entityType,
                                String description, Double anomalyScore);
}
