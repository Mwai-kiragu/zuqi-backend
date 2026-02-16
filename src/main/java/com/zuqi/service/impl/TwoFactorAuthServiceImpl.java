package com.zuqi.service.impl;

import com.zuqi.api.dto.user.EnableTwoFactorRequest;
import com.zuqi.api.dto.user.EnableTwoFactorResponse;
import com.zuqi.domain.user.TwoFactorAuth;
import com.zuqi.domain.user.TwoFactorAuth.TwoFactorType;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.TwoFactorAuthRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.service.TwoFactorAuthService;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TwoFactorAuthServiceImpl implements TwoFactorAuthService {

    private final TwoFactorAuthRepository twoFactorAuthRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.name:Zuqi}")
    private String appName;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    @Override
    @Transactional
    public EnableTwoFactorResponse initializeTwoFactor(UUID userId, EnableTwoFactorRequest request) {
        log.info("Initializing 2FA for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        // Check if 2FA is already enabled
        if (user.isTwoFactorEnabled()) {
            throw new ValidationException("Two-factor authentication is already enabled");
        }

        // Delete any existing unverified 2FA setup
        twoFactorAuthRepository.findByUserId(userId)
                .ifPresent(existing -> {
                    if (!existing.isVerified()) {
                        twoFactorAuthRepository.delete(existing);
                    }
                });

        TwoFactorType type = request.getType();

        if (type == TwoFactorType.TOTP) {
            return initializeTotpTwoFactor(user);
        } else if (type == TwoFactorType.SMS) {
            return initializeSmsTwoFactor(user, request.getPhoneNumber());
        } else if (type == TwoFactorType.EMAIL) {
            return initializeEmailTwoFactor(user);
        }

        throw new ValidationException("Unsupported two-factor authentication type");
    }

    private EnableTwoFactorResponse initializeTotpTwoFactor(User user) {
        // Generate secret key
        String secretKey = secretGenerator.generate();

        // Generate backup codes
        List<String> backupCodes = generateBackupCodes();
        List<String> hashedBackupCodes = backupCodes.stream()
                .map(passwordEncoder::encode)
                .collect(Collectors.toList());

        // Create 2FA record
        TwoFactorAuth twoFactorAuth = TwoFactorAuth.builder()
                .user(user)
                .type(TwoFactorType.TOTP)
                .secretKey(secretKey)
                .backupCodes(hashedBackupCodes)
                .backupCodesRemaining(backupCodes.size())
                .verified(false)
                .build();

        twoFactorAuthRepository.save(twoFactorAuth);

        // Generate QR code URL
        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secretKey)
                .issuer(appName)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        String qrCodeUrl = getQrCodeUrl(qrData);

        log.info("TOTP 2FA initialized for user: {}", user.getId());

        return EnableTwoFactorResponse.builder()
                .type(TwoFactorType.TOTP)
                .secretKey(secretKey)
                .qrCodeUrl(qrCodeUrl)
                .backupCodes(backupCodes)
                .message("Scan the QR code with your authenticator app and enter the code to verify")
                .build();
    }

    private EnableTwoFactorResponse initializeSmsTwoFactor(User user, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            phoneNumber = user.getPhoneNumber();
        }

        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new ValidationException("Phone number is required for SMS-based 2FA");
        }

        // Generate backup codes
        List<String> backupCodes = generateBackupCodes();
        List<String> hashedBackupCodes = backupCodes.stream()
                .map(passwordEncoder::encode)
                .collect(Collectors.toList());

        // Create 2FA record
        TwoFactorAuth twoFactorAuth = TwoFactorAuth.builder()
                .user(user)
                .type(TwoFactorType.SMS)
                .phoneNumber(phoneNumber)
                .backupCodes(hashedBackupCodes)
                .backupCodesRemaining(backupCodes.size())
                .verified(false)
                .build();

        twoFactorAuthRepository.save(twoFactorAuth);

        // TODO: Send verification SMS here

        log.info("SMS 2FA initialized for user: {}", user.getId());

        return EnableTwoFactorResponse.builder()
                .type(TwoFactorType.SMS)
                .backupCodes(backupCodes)
                .message("A verification code has been sent to your phone")
                .build();
    }

    private EnableTwoFactorResponse initializeEmailTwoFactor(User user) {
        // Generate backup codes
        List<String> backupCodes = generateBackupCodes();
        List<String> hashedBackupCodes = backupCodes.stream()
                .map(passwordEncoder::encode)
                .collect(Collectors.toList());

        // Create 2FA record
        TwoFactorAuth twoFactorAuth = TwoFactorAuth.builder()
                .user(user)
                .type(TwoFactorType.EMAIL)
                .backupCodes(hashedBackupCodes)
                .backupCodesRemaining(backupCodes.size())
                .verified(false)
                .build();

        twoFactorAuthRepository.save(twoFactorAuth);

        // TODO: Send verification email here

        log.info("Email 2FA initialized for user: {}", user.getId());

        return EnableTwoFactorResponse.builder()
                .type(TwoFactorType.EMAIL)
                .backupCodes(backupCodes)
                .message("A verification code has been sent to your email")
                .build();
    }

    @Override
    @Transactional
    public boolean verifyAndEnable(UUID userId, String code) {
        log.info("Verifying 2FA code for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new ValidationException("Two-factor authentication not initialized"));

        if (twoFactorAuth.isVerified()) {
            throw new ValidationException("Two-factor authentication is already verified");
        }

        boolean isValid = false;

        if (twoFactorAuth.getType() == TwoFactorType.TOTP) {
            isValid = codeVerifier.isValidCode(twoFactorAuth.getSecretKey(), code);
        } else {
            // For SMS/Email, compare with stored code (implementation needed)
            // For now, accept any 6-digit code for testing
            isValid = code != null && code.matches("\\d{6}");
        }

        if (isValid) {
            twoFactorAuth.setVerified(true);
            twoFactorAuth.setLastUsedAt(LocalDateTime.now());
            twoFactorAuthRepository.save(twoFactorAuth);

            user.setTwoFactorEnabled(true);
            userRepository.save(user);

            log.info("2FA enabled successfully for user: {}", userId);
            return true;
        }

        log.warn("Invalid 2FA verification code for user: {}", userId);
        return false;
    }

    @Override
    @Transactional
    public void disableTwoFactor(UUID userId, String currentPassword) {
        log.info("Disabling 2FA for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        // Verify password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ValidationException("Current password is incorrect");
        }

        // Delete 2FA record
        twoFactorAuthRepository.findByUserId(userId)
                .ifPresent(twoFactorAuthRepository::delete);

        // Update user
        user.setTwoFactorEnabled(false);
        userRepository.save(user);

        log.info("2FA disabled for user: {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyCode(UUID userId, String code) {
        log.info("Verifying 2FA code during login for user: {}", userId);

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new ValidationException("Two-factor authentication not configured"));

        if (!twoFactorAuth.isVerified()) {
            throw new ValidationException("Two-factor authentication not verified");
        }

        boolean isValid = false;

        if (twoFactorAuth.getType() == TwoFactorType.TOTP) {
            isValid = codeVerifier.isValidCode(twoFactorAuth.getSecretKey(), code);
        } else {
            // For SMS/Email, compare with stored code (implementation needed)
            isValid = code != null && code.matches("\\d{6}");
        }

        if (isValid) {
            twoFactorAuthRepository.updateLastUsedAt(userId, LocalDateTime.now());
        }

        return isValid;
    }

    @Override
    @Transactional
    public boolean verifyBackupCode(UUID userId, String code) {
        log.info("Verifying backup code for user: {}", userId);

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new ValidationException("Two-factor authentication not configured"));

        if (twoFactorAuth.getBackupCodes() == null || twoFactorAuth.getBackupCodes().isEmpty()) {
            return false;
        }

        // Check each backup code
        List<String> remainingCodes = new ArrayList<>(twoFactorAuth.getBackupCodes());
        for (int i = 0; i < remainingCodes.size(); i++) {
            if (passwordEncoder.matches(code, remainingCodes.get(i))) {
                // Remove used backup code
                remainingCodes.remove(i);
                twoFactorAuth.setBackupCodes(remainingCodes);
                twoFactorAuth.setBackupCodesRemaining(remainingCodes.size());
                twoFactorAuth.setLastUsedAt(LocalDateTime.now());
                twoFactorAuthRepository.save(twoFactorAuth);

                log.info("Backup code used for user: {}, {} codes remaining", userId, remainingCodes.size());
                return true;
            }
        }

        return false;
    }

    @Override
    @Transactional
    public List<String> regenerateBackupCodes(UUID userId) {
        log.info("Regenerating backup codes for user: {}", userId);

        TwoFactorAuth twoFactorAuth = twoFactorAuthRepository.findByUserId(userId)
                .orElseThrow(() -> new ValidationException("Two-factor authentication not configured"));

        if (!twoFactorAuth.isVerified()) {
            throw new ValidationException("Two-factor authentication not verified");
        }

        List<String> backupCodes = generateBackupCodes();
        List<String> hashedBackupCodes = backupCodes.stream()
                .map(passwordEncoder::encode)
                .collect(Collectors.toList());

        twoFactorAuth.setBackupCodes(hashedBackupCodes);
        twoFactorAuth.setBackupCodesRemaining(backupCodes.size());
        twoFactorAuthRepository.save(twoFactorAuth);

        log.info("Backup codes regenerated for user: {}", userId);
        return backupCodes;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTwoFactorEnabled(UUID userId) {
        return userRepository.findById(userId)
                .map(User::isTwoFactorEnabled)
                .orElse(false);
    }

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < 10; i++) {
            // Generate 8-character alphanumeric code
            StringBuilder code = new StringBuilder();
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            for (int j = 0; j < 8; j++) {
                code.append(chars.charAt(random.nextInt(chars.length())));
            }
            // Format as XXXX-XXXX
            codes.add(code.substring(0, 4) + "-" + code.substring(4, 8));
        }

        return codes;
    }

    private String getQrCodeUrl(QrData qrData) {
        // Returns otpauth:// URL format
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=%s&digits=%d&period=%d",
                qrData.getIssuer(),
                qrData.getLabel(),
                qrData.getSecret(),
                qrData.getIssuer(),
                qrData.getAlgorithm(),
                qrData.getDigits(),
                qrData.getPeriod());
    }
}
