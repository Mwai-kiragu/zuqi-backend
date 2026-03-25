package com.zuqi.service.impl;

import com.zuqi.config.AppConfig;
import com.zuqi.config.EmailConfig;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.user.User;
import com.zuqi.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "SES")
@Slf4j
public class SesEmailServiceImpl implements EmailService {

    private final SesClient sesClient;
    private final SpringTemplateEngine emailTemplateEngine;
    private final EmailConfig emailConfig;
    private final AppConfig appConfig;

    public SesEmailServiceImpl(EmailConfig emailConfig, SpringTemplateEngine emailTemplateEngine, AppConfig appConfig) {
        this.emailConfig = emailConfig;
        this.emailTemplateEngine = emailTemplateEngine;
        this.appConfig = appConfig;

        EmailConfig.SesProperties ses = emailConfig.getSes();
        this.sesClient = SesClient.builder()
                .region(Region.of(ses.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ses.getAccessKey(), ses.getSecretKey())
                ))
                .build();
    }

    @Override
    @Async
    public void sendWelcomeEmail(User user, String temporaryPassword) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Credentials — email: {}, password: {}", user.getEmail(), temporaryPassword);
            return;
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("userName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        vars.put("email", user.getEmail());
        vars.put("temporaryPassword", temporaryPassword);
        vars.put("loginUrl", appConfig.getUrl() + "/login");
        vars.put("companyName", emailConfig.getFromName());
        sendTemplatedEmail(user.getEmail(),
                "Welcome to " + emailConfig.getFromName() + " - Your Account Details",
                "welcome", vars);
    }

    @Override
    @Async
    public void sendPasswordResetEmail(User user, String resetToken) {
        if (!emailConfig.isEnabled()) return;
        Map<String, Object> vars = new HashMap<>();
        vars.put("userName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        vars.put("resetUrl", appConfig.getUrl() + "/reset-password?token=" + resetToken);
        vars.put("expiryMinutes", 60);
        vars.put("companyName", emailConfig.getFromName());
        sendTemplatedEmail(user.getEmail(),
                "Password Reset Request - " + emailConfig.getFromName(),
                "password-reset", vars);
    }

    @Override
    @Async
    public void sendPasswordResetOtpEmail(User user, String otp) {
        if (!emailConfig.isEnabled()) return;
        Map<String, Object> vars = new HashMap<>();
        vars.put("userName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        vars.put("otp", otp);
        vars.put("expiryMinutes", 10);
        vars.put("companyName", emailConfig.getFromName());
        sendTemplatedEmail(user.getEmail(),
                "Your Password Reset Code - " + emailConfig.getFromName(),
                "password-reset-otp", vars);
    }

    @Override
    @Async
    public void sendEmailVerificationOtpEmail(User user, String otp) {
        if (!emailConfig.isEnabled()) return;
        Map<String, Object> vars = new HashMap<>();
        vars.put("userName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        vars.put("otp", otp);
        vars.put("expiryMinutes", 10);
        vars.put("companyName", emailConfig.getFromName());
        sendTemplatedEmail(user.getEmail(),
                "Verify Your Email - " + emailConfig.getFromName(),
                "email-verification-otp", vars);
    }

    @Override
    @Async
    public void sendPasswordChangedEmail(User user) {
        if (!emailConfig.isEnabled()) return;
        Map<String, Object> vars = new HashMap<>();
        vars.put("userName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());
        vars.put("companyName", emailConfig.getFromName());
        vars.put("supportEmail", emailConfig.getFrom());
        sendTemplatedEmail(user.getEmail(),
                "Your Password Has Been Changed - " + emailConfig.getFromName(),
                "password-changed", vars);
    }

    @Override
    @Async
    public void sendCustomerOnboardingEmail(Customer customer) {
        if (customer.getEmail() == null || customer.getEmail().isBlank()) return;
        if (!emailConfig.isEnabled()) return;
        String distributorName = customer.getDistributor() != null
                ? customer.getDistributor().getName() : emailConfig.getFromName();
        Map<String, Object> vars = new HashMap<>();
        vars.put("businessName", customer.getBusinessName());
        vars.put("ownerName", customer.getOwnerName());
        vars.put("customerCode", customer.getCustomerCode());
        vars.put("phone", customer.getPhone());
        vars.put("paymentTermsDays", customer.getPaymentTermsDays());
        vars.put("distributorName", distributorName);
        vars.put("companyName", emailConfig.getFromName());
        sendTemplatedEmail(customer.getEmail(),
                "You've been added as a customer of " + distributorName,
                "customer-onboarding", vars);
    }

    @Override
    @Async
    public void sendAnomalyAlertEmail(String to, String alertType, String severity,
                                       String entityType, String description, Double anomalyScore) {
        if (!emailConfig.isEnabled()) return;
        Map<String, Object> vars = new HashMap<>();
        vars.put("alertType", alertType);
        vars.put("severity", severity);
        vars.put("entityType", entityType);
        vars.put("description", description);
        vars.put("anomalyScore", anomalyScore != null ? String.format("%.2f", anomalyScore) : "N/A");
        vars.put("companyName", emailConfig.getFromName());
        vars.put("dashboardUrl", appConfig.getUrl() + "/dashboard/alerts");
        sendTemplatedEmail(to,
                String.format("[%s] %s Alert - %s", severity, alertType.replace("_", " "), emailConfig.getFromName()),
                "anomaly-alert", vars);
    }

    @Override
    @Async
    public void sendInvoiceEmailAsync(String to, String subject, Map<String, Object> contextVariables) {
        if (!emailConfig.isEnabled()) return;
        sendTemplatedEmail(to, subject, "invoice", contextVariables);
    }

    @Override
    public void sendTemplatedEmail(String to, String subject, String templateName, Map<String, Object> contextVariables) {
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled (SES). Would send '{}' to: {}", templateName, to);
            return;
        }
        try {
            Map<String, Object> enriched = new HashMap<>(contextVariables);
            enriched.putIfAbsent("logoUrl", appConfig.getUrl() + "/zuqi-logo.png");

            Context context = new Context();
            context.setVariables(enriched);
            String htmlBody = emailTemplateEngine.process(templateName, context);

            SendEmailRequest request = SendEmailRequest.builder()
                    .source(emailConfig.getFromName() + " <" + emailConfig.getFrom() + ">")
                    .destination(Destination.builder().toAddresses(to).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(request);
            log.info("SES email sent to: {} template: {}", to, templateName);

        } catch (SesException e) {
            log.error("SES failed to send email to: {} template: {} — {}", to, templateName, e.awsErrorDetails().errorMessage());
            throw new RuntimeException("SES email delivery failed", e);
        } catch (Exception e) {
            log.error("Failed to send SES email to: {} template: {}", to, templateName, e);
            throw new RuntimeException("Failed to send email via SES", e);
        }
    }
}
