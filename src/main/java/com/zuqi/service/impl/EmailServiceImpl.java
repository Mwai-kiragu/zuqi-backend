package com.zuqi.service.impl;

import com.zuqi.config.AppConfig;
import com.zuqi.config.EmailConfig;
import com.zuqi.domain.user.User;
import com.zuqi.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine emailTemplateEngine;
    private final EmailConfig emailConfig;
    private final AppConfig appConfig;

    @Override
    @Async
    public void sendWelcomeEmail(User user, String temporaryPassword) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Merchant credentials — email: {}, password: {}", user.getEmail(), temporaryPassword);
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        variables.put("email", user.getEmail());
        variables.put("temporaryPassword", temporaryPassword);
        variables.put("loginUrl", appConfig.getUrl() + "/login");
        variables.put("companyName", emailConfig.getFromName());

        sendTemplatedEmail(
                user.getEmail(),
                "Welcome to " + emailConfig.getFromName() + " - Your Account Details",
                "welcome",
                variables
        );
    }

    @Override
    @Async
    public void sendPasswordResetEmail(User user, String resetToken) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send password reset email to: {}", user.getEmail());
            return;
        }

        String resetUrl = appConfig.getUrl() + "/reset-password?token=" + resetToken;

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        variables.put("resetUrl", resetUrl);
        variables.put("expiryMinutes", 60); // Token expires in 60 minutes
        variables.put("companyName", emailConfig.getFromName());

        sendTemplatedEmail(
                user.getEmail(),
                "Password Reset Request - " + emailConfig.getFromName(),
                "password-reset",
                variables
        );
    }

    @Override
    @Async
    public void sendPasswordResetOtpEmail(User user, String otp) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send password reset OTP email to: {}", user.getEmail());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        variables.put("otp", otp);
        variables.put("expiryMinutes", 10); // OTP expires in 10 minutes
        variables.put("companyName", emailConfig.getFromName());

        sendTemplatedEmail(
                user.getEmail(),
                "Your Password Reset Code - " + emailConfig.getFromName(),
                "password-reset-otp",
                variables
        );
    }

    @Override
    @Async
    public void sendEmailVerificationOtpEmail(User user, String otp) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send email verification OTP to: {}", user.getEmail());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        variables.put("otp", otp);
        variables.put("expiryMinutes", 10);
        variables.put("companyName", emailConfig.getFromName());

        sendTemplatedEmail(
                user.getEmail(),
                "Verify Your Email - " + emailConfig.getFromName(),
                "email-verification-otp",
                variables
        );
    }

    @Override
    @Async
    public void sendPasswordChangedEmail(User user) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send password changed email to: {}", user.getEmail());
            return;
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        variables.put("companyName", emailConfig.getFromName());
        variables.put("supportEmail", emailConfig.getFrom());

        sendTemplatedEmail(
                user.getEmail(),
                "Your Password Has Been Changed - " + emailConfig.getFromName(),
                "password-changed",
                variables
        );
    }

    @Override
    public void sendTemplatedEmail(String to, String subject, String templateName, Map<String, Object> contextVariables) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send '{}' email to: {}", templateName, to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            Context context = new Context();
            context.setVariables(contextVariables);
            String htmlContent = emailTemplateEngine.process(templateName, context);

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(emailConfig.getFrom(), emailConfig.getFromName());

            mailSender.send(message);
            log.info("Email sent successfully to: {} with template: {}", to, templateName);

        } catch (MessagingException e) {
            log.error("Failed to send email to: {} with template: {}", to, templateName, e);
            throw new RuntimeException("Failed to send email", e);
        } catch (java.io.UnsupportedEncodingException e) {
            log.error("Unsupported encoding for email from name", e);
            throw new RuntimeException("Failed to set email from name", e);
        }
    }

    @Override
    @Async
    public void sendAnomalyAlertEmail(String to, String alertType, String severity, String entityType,
                                       String description, Double anomalyScore) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send anomaly alert email to: {}", to);
            return;
        }

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("alertType", alertType);
            variables.put("severity", severity);
            variables.put("entityType", entityType);
            variables.put("description", description);
            variables.put("anomalyScore", anomalyScore != null ? String.format("%.2f", anomalyScore) : "N/A");
            variables.put("companyName", emailConfig.getFromName());
            variables.put("dashboardUrl", appConfig.getUrl() + "/dashboard/alerts");

            sendTemplatedEmail(
                    to,
                    String.format("[%s] %s Alert - %s", severity, alertType.replace("_", " "), emailConfig.getFromName()),
                    "anomaly-alert",
                    variables
            );
            log.info("Anomaly alert email sent to: {} for {} {}", to, severity, alertType);
        } catch (Exception e) {
            log.error("Failed to send anomaly alert email to: {}", to, e);
        }
    }

    @Override
    @Async
    public void sendInvoiceEmailAsync(String to, String subject, Map<String, Object> contextVariables) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send invoice email to: {}", to);
            return;
        }

        try {
            sendTemplatedEmail(to, subject, "invoice", contextVariables);
            log.info("Invoice email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send invoice email to: {}", to, e);
        }
    }
}
