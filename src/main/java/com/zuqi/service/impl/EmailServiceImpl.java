package com.zuqi.service.impl;

import com.zuqi.config.AppConfig;
import com.zuqi.config.EmailConfig;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.domain.user.User;
import com.zuqi.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "app.email.provider", havingValue = "SMTP", matchIfMissing = true)
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
    @Async
    public void sendCustomerOnboardingEmail(Customer customer) {
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            log.debug("Customer onboarding email skipped — no email address for customer {}", customer.getId());
            return;
        }
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send onboarding email to customer: {}", customer.getEmail());
            return;
        }

        String distributorName = customer.getDistributor() != null ? customer.getDistributor().getName() : emailConfig.getFromName();

        Map<String, Object> variables = new HashMap<>();
        variables.put("businessName", customer.getBusinessName());
        variables.put("ownerName", customer.getOwnerName());
        variables.put("customerCode", customer.getCustomerCode());
        variables.put("phone", customer.getPhone());
        variables.put("paymentTermsDays", customer.getPaymentTermsDays());
        variables.put("distributorName", distributorName);
        variables.put("companyName", emailConfig.getFromName());

        sendTemplatedEmail(
                customer.getEmail(),
                "You've been added as a customer of " + distributorName,
                "customer-onboarding",
                variables
        );
    }

    @Override
    @Async
    public void sendSupplierOnboardingEmail(Supplier supplier) {
        if (supplier.getEmail() == null || supplier.getEmail().isBlank()) {
            log.debug("Supplier onboarding email skipped — no email address for supplier {}", supplier.getId());
            return;
        }
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send onboarding email to supplier: {}", supplier.getEmail());
            return;
        }

        String distributorName = supplier.getDistributor() != null
                ? supplier.getDistributor().getName() : emailConfig.getFromName();

        Map<String, Object> variables = new HashMap<>();
        variables.put("supplierName", supplier.getName());
        variables.put("supplierCode", supplier.getSupplierCode());
        variables.put("phone", supplier.getPhone());
        variables.put("kraPin", supplier.getKraPin());
        variables.put("paymentTermsDays", supplier.getPaymentTermsDays());
        variables.put("distributorName", distributorName);
        variables.put("companyName", emailConfig.getFromName());

        sendTemplatedEmail(
                supplier.getEmail(),
                "Your supplier account has been approved — " + distributorName,
                "supplier-onboarding",
                variables
        );
    }

    @Override
    @Async
    public void sendPurchaseOrderEmail(PurchaseOrder po, String distributorName, java.util.Map<String, String> confirmationTokens) {
        if (po.getSupplier() == null || po.getSupplier().getEmail() == null || po.getSupplier().getEmail().isBlank()) {
            log.info("PO email skipped — supplier {} has no email address", po.getSupplier() != null ? po.getSupplier().getId() : "null");
            return;
        }
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send PO {} to supplier: {}", po.getPoNumber(), po.getSupplier().getEmail());
            return;
        }

        try {
            String frontendBase = appConfig.getUrl();
            Map<String, Object> variables = new HashMap<>();
            variables.put("poNumber", po.getPoNumber());
            variables.put("supplierName", po.getSupplier().getName());
            variables.put("distributorName", distributorName != null ? distributorName : emailConfig.getFromName());
            variables.put("sentAt", po.getSentAt() != null ? po.getSentAt().toLocalDate().toString() : java.time.LocalDate.now().toString());
            variables.put("expectedDeliveryDate", po.getExpectedDeliveryDate() != null ? po.getExpectedDeliveryDate().toString() : "—");
            variables.put("deliveryAddress", po.getDeliveryAddress());
            variables.put("paymentTermsDays", po.getPaymentTermsDays());
            variables.put("totalAmount", po.getTotalAmount());
            variables.put("items", po.getItems() != null ? po.getItems() : java.util.Collections.emptyList());
            variables.put("notes", po.getNotes());
            variables.put("companyName", emailConfig.getFromName());

            if (confirmationTokens != null) {
                variables.put("confirmUrl",  frontendBase + "/po-confirm/" + confirmationTokens.getOrDefault("CONFIRM", ""));
                variables.put("declineUrl",  frontendBase + "/po-confirm/" + confirmationTokens.getOrDefault("DECLINE", ""));
                variables.put("partialUrl",  frontendBase + "/po-confirm/" + confirmationTokens.getOrDefault("PARTIAL", ""));
            }

            sendTemplatedEmail(
                    po.getSupplier().getEmail(),
                    "Purchase Order " + po.getPoNumber() + " from " + (distributorName != null ? distributorName : emailConfig.getFromName()),
                    "po-notification",
                    variables
            );
            log.info("PO email sent to supplier {} for PO {}", po.getSupplier().getEmail(), po.getPoNumber());
        } catch (Exception e) {
            log.error("Failed to send PO email for PO {}: {}", po.getPoNumber(), e.getMessage(), e);
        }
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

            Map<String, Object> enriched = new HashMap<>(contextVariables);
            enriched.putIfAbsent("logoUrl", appConfig.getUrl() + "/zuqi-logo.png");

            Context context = new Context();
            context.setVariables(enriched);
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

    @Override
    @Async
    public void sendDataExportEmail(String to, String name, String entityType, int recordCount,
                                    String csvContent, String attachmentFilename) {
        if (to == null || to.isBlank()) {
            log.warn("sendDataExportEmail: no email address for user, skipping");
            return;
        }
        if (!emailConfig.isEnabled()) {
            log.info("Email disabled. Would send data export ({}) to: {}", entityType, to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    java.nio.charset.StandardCharsets.UTF_8.name()
            );

            helper.setTo(to);
            helper.setFrom(emailConfig.getFrom(), emailConfig.getFromName());
            helper.setSubject("Your " + entityType + " export is ready — " + recordCount + " records");

            String greeting = (name != null && !name.isBlank()) ? "Hi " + name + "," : "Hello,";
            String html = "<div style='font-family:sans-serif;color:#111;'>"
                + "<p>" + greeting + "</p>"
                + "<p>Your <strong>" + entityType + "</strong> export has been prepared and is attached to this email.</p>"
                + "<p><strong>" + recordCount + " records</strong> were included in the export.</p>"
                + "<p style='color:#6b7280;font-size:13px;'>This export was requested from the Zuqi platform. "
                + "If you did not request this, please contact your administrator.</p>"
                + "<p>— " + emailConfig.getFromName() + "</p>"
                + "</div>";

            helper.setText(html, true);

            // Attach the CSV file
            byte[] csvBytes = csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            helper.addAttachment(attachmentFilename,
                new org.springframework.core.io.ByteArrayResource(csvBytes), "text/csv");

            mailSender.send(message);
            log.info("Data export email ({}) sent to: {} — {} records", entityType, to, recordCount);

        } catch (Exception e) {
            log.error("Failed to send data export email to: {}", to, e);
        }
    }
}
