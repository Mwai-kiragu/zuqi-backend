package com.zuqi.service.impl;

import com.zuqi.api.dto.auth.AuthenticationRequest;
import com.zuqi.api.dto.auth.AuthenticationResponse;
import com.zuqi.api.dto.auth.RefreshTokenRequest;
import com.zuqi.api.dto.auth.RegisterRequest;
import com.zuqi.api.dto.auth.DistributorRegisterRequest;
import com.zuqi.api.dto.auth.MerchantRegisterRequest;
import com.zuqi.api.dto.auth.ForgotPasswordRequest;
import com.zuqi.api.dto.auth.ResetPasswordRequest;
import com.zuqi.api.dto.auth.VerifyOtpRequest;
import com.zuqi.api.dto.billing.AssignSubscriptionRequest;
import com.zuqi.domain.billing.BillingPackageType;
import com.zuqi.domain.branch.BranchStatus;
import com.zuqi.domain.branch.BranchUser;
import com.zuqi.domain.branch.BranchUserStatus;
import com.zuqi.domain.branch.DistributorBranch;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.user.PasswordResetToken;
import com.zuqi.domain.user.RefreshToken;
import com.zuqi.domain.user.Role;
import com.zuqi.domain.user.RoleName;
import com.zuqi.domain.user.TokenPurpose;
import com.zuqi.domain.user.User;
import com.zuqi.exception.AuthenticationException;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.BranchUserRepository;
import com.zuqi.repository.DistributorBranchRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.repository.PasswordResetTokenRepository;
import com.zuqi.repository.RefreshTokenRepository;
import com.zuqi.repository.RoleRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.security.JwtService;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.AuthenticationService;
import com.zuqi.service.BillingService;
import com.zuqi.service.EmailService;
import com.zuqi.service.GlAccountService;
import com.zuqi.service.GlPeriodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DistributorRepository distributorRepository;
    private final MerchantRepository merchantRepository;
    // CustomerRepository no longer needed here (KYC via merchant brand or distributor)
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final BillingService billingService;
    private final ActivityLogService activityLogService;
    private final DistributorBranchRepository distributorBranchRepository;
    private final BranchUserRepository branchUserRepository;
    private final GlAccountService glAccountService;
    private final GlPeriodService glPeriodService;

    // OTP expires in 10 minutes
    private static final int OTP_EXPIRY_MINUTES = 10;

    private String resolveKycStatus(User user) {
        // Brand Merchant admin: resolve KYC from Merchant entity
        if (user.getMerchantId() != null) {
            return merchantRepository.findById(user.getMerchantId())
                    .map(m -> m.getKycStatus().name())
                    .orElse("PENDING");
        }
        // Distributor staff: resolve KYC from Distributor entity
        if (user.getDistributorId() != null) {
            return distributorRepository.findById(user.getDistributorId())
                    .map(d -> d.getKycStatus() != null ? d.getKycStatus().name() : "PENDING")
                    .orElse("PENDING");
        }
        return null;
    }

    @Override
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        // Check if phone number already exists (if provided)
        if (request.getPhoneNumber() != null && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicateResourceException("User", "phoneNumber", request.getPhoneNumber());
        }

        // Get default role (CUSTOMER for self-registration)
        Set<Role> roles = new HashSet<>();
        Role defaultRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "CUSTOMER"));
        roles.add(defaultRole);

        // Create new user
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(roles)
                .active(true)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        // Send email verification OTP
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);
        PasswordResetToken verificationToken = PasswordResetToken.builder()
                .token(otp)
                .user(savedUser)
                .expiresAt(expiresAt)
                .used(false)
                .purpose(TokenPurpose.EMAIL_VERIFICATION)
                .build();
        passwordResetTokenRepository.save(verificationToken);
        emailService.sendEmailVerificationOtpEmail(savedUser, otp);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshToken = createRefreshToken(savedUser);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .roles(savedUser.getRoles().stream().map(r -> r.getName()).toList())
                .distributorId(savedUser.getDistributorId())
                .merchantId(savedUser.getMerchantId())
                .phoneNumber(savedUser.getPhoneNumber())
                .emailVerified(savedUser.isEmailVerified())
                .kycStatus(resolveKycStatus(savedUser))
                .build();
    }

    @Override
    @Transactional
    public AuthenticationResponse registerDistributor(DistributorRegisterRequest request) {
        log.info("Registering new distributor with email: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        // Check if phone number already exists (if provided)
        if (request.getPhoneNumber() != null && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicateResourceException("User", "phoneNumber", request.getPhoneNumber());
        }

        // Check if company name already exists
        if (distributorRepository.existsByName(request.getCompanyName())) {
            throw new DuplicateResourceException("Distributor", "name", request.getCompanyName());
        }

        // 1. Create Distributor record
        Distributor distributor = Distributor.builder()
                .name(request.getCompanyName())
                .registrationNumber(request.getRegistrationNumber())
                .email(request.getEmail())
                .phone(request.getCompanyPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry() != null ? request.getCountry() : "Kenya")
                .active(true)
                .build();

        Distributor savedDistributor = distributorRepository.save(distributor);

        // 2. Get DISTRIBUTOR_ADMIN role
        Set<Role> roles = new HashSet<>();
        Role distributorAdminRole = roleRepository.findByName(RoleName.DISTRIBUTOR_ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "DISTRIBUTOR_ADMIN"));
        roles.add(distributorAdminRole);

        // 3. Create User linked to Distributor
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(roles)
                .distributorId(savedDistributor.getId())
                .active(true)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);

        // 3b. Auto-create the default Head Office branch for this distributor
        DistributorBranch hqBranch = DistributorBranch.builder()
                .distributor(savedDistributor)
                .name(savedDistributor.getName() + " - Head Office")
                .code("HQ")
                .headquarters(true)
                .status(BranchStatus.ACTIVE)
                .build();
        DistributorBranch savedHqBranch = distributorBranchRepository.save(hqBranch);

        // 3c. Assign the admin user to the Head Office branch
        BranchUser adminBranchUser = BranchUser.builder()
                .branch(savedHqBranch)
                .user(savedUser)
                .role("DISTRIBUTOR_ADMIN")
                .status(BranchUserStatus.ACTIVE)
                .build();
        branchUserRepository.save(adminBranchUser);
        log.info("Created default HQ branch {} for distributor {}", savedHqBranch.getId(), savedDistributor.getId());

        // Auto-seed GL accounts + create all 12 periods for the current year
        autoSetupGl(savedDistributor, savedUser);

        // 4. Assign FREE_TRIAL subscription
        AssignSubscriptionRequest subRequest = new AssignSubscriptionRequest();
        subRequest.setDistributorId(savedDistributor.getId());
        subRequest.setPackageType(BillingPackageType.FREE_TRIAL);
        billingService.assign(subRequest, savedUser);

        log.info("Distributor registered: {}, User: {}", savedDistributor.getId(), savedUser.getId());

        // 5. Send email verification OTP
        String otp = generateOtp();
        LocalDateTime otpExpiry = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);
        PasswordResetToken verificationToken = PasswordResetToken.builder()
                .token(otp)
                .user(savedUser)
                .expiresAt(otpExpiry)
                .used(false)
                .purpose(TokenPurpose.EMAIL_VERIFICATION)
                .build();
        passwordResetTokenRepository.save(verificationToken);
        emailService.sendEmailVerificationOtpEmail(savedUser, otp);

        // 6. Generate tokens
        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshToken = createRefreshToken(savedUser);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .roles(savedUser.getRoles().stream().map(r -> r.getName()).toList())
                .distributorId(savedDistributor.getId())
                .phoneNumber(savedUser.getPhoneNumber())
                .emailVerified(savedUser.isEmailVerified())
                .kycStatus(resolveKycStatus(savedUser))
                .build();
    }

    @Override
    @Transactional
    public AuthenticationResponse registerMerchant(MerchantRegisterRequest request) {
        log.info("Registering new merchant brand with email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        if (request.getPhoneNumber() != null && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicateResourceException("User", "phoneNumber", request.getPhoneNumber());
        }

        // 1. Create Merchant brand entity
        Merchant merchantBrand = Merchant.builder()
                .name(request.getBrandName())
                .registrationNumber(request.getRegistrationNumber())
                .email(request.getEmail())
                .phone(request.getBrandPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry() != null ? request.getCountry() : "Kenya")
                .active(true)
                .build();
        Merchant savedMerchant = merchantRepository.save(merchantBrand);

        // 2. Create Distributor linked to this Merchant
        String distributorName = request.getCompanyName() != null ? request.getCompanyName() : request.getBrandName();
        Distributor distributor = Distributor.builder()
                .name(distributorName)
                .registrationNumber(request.getRegistrationNumber())
                .email(request.getEmail())
                .phone(request.getBrandPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry() != null ? request.getCountry() : "Kenya")
                .merchant(savedMerchant)
                .active(true)
                .build();
        Distributor savedDistributor = distributorRepository.save(distributor);

        // 3. Get MERCHANT_ADMIN role
        Set<Role> roles = new HashSet<>();
        Role merchantAdminRole = roleRepository.findByName(RoleName.MERCHANT_ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "MERCHANT_ADMIN"));
        roles.add(merchantAdminRole);

        // 4. Create User linked to both Merchant brand and Distributor
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(roles)
                .distributorId(savedDistributor.getId())
                .merchantId(savedMerchant.getId())
                .active(true)
                .emailVerified(false)
                .mustChangePassword(true)
                .build();
        User savedUser = userRepository.save(user);

        // 5. Auto-create HQ branch for the Distributor
        DistributorBranch hqBranch = DistributorBranch.builder()
                .distributor(savedDistributor)
                .name(savedDistributor.getName() + " - Head Office")
                .code("HQ")
                .headquarters(true)
                .status(BranchStatus.ACTIVE)
                .build();
        DistributorBranch savedHqBranch = distributorBranchRepository.save(hqBranch);

        // 6. Assign admin user to HQ branch
        BranchUser adminBranchUser = BranchUser.builder()
                .branch(savedHqBranch)
                .user(savedUser)
                .role("MERCHANT_ADMIN")
                .status(BranchUserStatus.ACTIVE)
                .build();
        branchUserRepository.save(adminBranchUser);

        // Auto-seed GL accounts + create all 12 periods for the current year
        autoSetupGl(savedDistributor, savedUser);

        // 7. Assign subscription package (defaults to FREE_TRIAL if not specified)
        AssignSubscriptionRequest subRequest = new AssignSubscriptionRequest();
        subRequest.setDistributorId(savedDistributor.getId());
        subRequest.setPackageType(request.getPackageType() != null ? request.getPackageType() : BillingPackageType.FREE_TRIAL);
        if (request.getCustomModules() != null && !request.getCustomModules().isEmpty()) {
            subRequest.setCustomModules(request.getCustomModules());
        }
        billingService.assign(subRequest, savedUser);

        log.info("Merchant brand registered: {}, Distributor: {}, User: {}",
                savedMerchant.getId(), savedDistributor.getId(), savedUser.getId());

        // 8. Send welcome email with login credentials
        emailService.sendWelcomeEmail(savedUser, request.getPassword());

        // 9. Send email verification OTP
        String otp = generateOtp();
        LocalDateTime otpExpiry = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);
        PasswordResetToken verificationToken = PasswordResetToken.builder()
                .token(otp)
                .user(savedUser)
                .expiresAt(otpExpiry)
                .used(false)
                .purpose(TokenPurpose.EMAIL_VERIFICATION)
                .build();
        passwordResetTokenRepository.save(verificationToken);
        emailService.sendEmailVerificationOtpEmail(savedUser, otp);

        // 9. Generate tokens
        String accessToken = jwtService.generateAccessToken(savedUser);
        String refreshToken = createRefreshToken(savedUser);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .roles(savedUser.getRoles().stream().map(r -> r.getName()).toList())
                .distributorId(savedDistributor.getId())
                .merchantId(savedMerchant.getId())
                .phoneNumber(savedUser.getPhoneNumber())
                .emailVerified(savedUser.isEmailVerified())
                .kycStatus(resolveKycStatus(savedUser))
                .mustChangePassword(savedUser.isMustChangePassword())
                .build();
    }

    @Override
    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        log.info("Authenticating user: {}", request.getEmail());

        // Authenticate with Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Find user
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = createRefreshToken(user);

        log.info("User authenticated successfully: {}", user.getEmail());

        activityLogService.log(user.getId(), user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                ActivityAction.LOGIN, "USER", user.getId(),
                user.getEmail(), "AUTH", "User logged in successfully");

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(user.getRoles().stream().map(r -> r.getName()).toList())
                .distributorId(user.getDistributorId())
                .merchantId(user.getMerchantId())
                .customerId(user.getCustomerId())
                .phoneNumber(user.getPhoneNumber())
                .emailVerified(user.isEmailVerified())
                .kycStatus(resolveKycStatus(user))
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    @Override
    @Transactional
    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        log.debug("Refreshing token");

        RefreshToken storedToken = refreshTokenRepository.findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new AuthenticationException("Invalid refresh token"));

        // Check if token is expired
        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new AuthenticationException("Refresh token has expired");
        }

        User user = storedToken.getUser();

        // Revoke old token
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Generate new tokens
        String accessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = createRefreshToken(user);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(user.getRoles().stream().map(r -> r.getName()).toList())
                .distributorId(user.getDistributorId())
                .merchantId(user.getMerchantId())
                .customerId(user.getCustomerId())
                .phoneNumber(user.getPhoneNumber())
                .emailVerified(user.isEmailVerified())
                .kycStatus(resolveKycStatus(user))
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        log.debug("Logging out user");

        refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .ifPresent(token -> {
                    User user = token.getUser();
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    activityLogService.log(user.getId(), user.getEmail(),
                            user.getFirstName() + " " + user.getLastName(),
                            ActivityAction.LOGOUT, "USER", user.getId(),
                            user.getEmail(), "AUTH", "User logged out");
                });
    }

    private String createRefreshToken(User user) {
        String token = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiration() / 1000))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return token;
    }

    @Override
    @Transactional
    public boolean forgotPassword(ForgotPasswordRequest request) {
        log.info("Processing forgot password request for email: {}", request.getEmail());

        // Find user by email
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

        if (userOptional.isEmpty()) {
            log.warn("Forgot password requested for non-existent email: {}", request.getEmail());
            return false;
        }

        User user = userOptional.get();

        // Check if user is active
        if (!user.isActive()) {
            log.warn("Forgot password requested for inactive user: {}", request.getEmail());
            return false;
        }

        // Generate 6-digit OTP
        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(otp)
                .user(user)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);

        // Send password reset OTP email
        emailService.sendPasswordResetOtpEmail(user, otp);
        log.info("Password reset OTP sent to: {}", user.getEmail());
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyOtp(VerifyOtpRequest request) {
        log.info("Verifying OTP for email: {}", request.getEmail());

        // Check if OTP is valid
        Optional<PasswordResetToken> tokenOptional = passwordResetTokenRepository
                .findValidOtpByEmailAndCode(request.getEmail(), request.getOtp(), LocalDateTime.now());

        if (tokenOptional.isEmpty()) {
            log.warn("Invalid or expired OTP for email: {}", request.getEmail());
            return false;
        }

        log.info("OTP verified successfully for email: {}", request.getEmail());
        return true;
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Processing password reset request for email: {}", request.getEmail());

        // Find and validate OTP by email and code
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findValidOtpByEmailAndCode(request.getEmail(), request.getOtp(), LocalDateTime.now())
                .orElseThrow(() -> new ValidationException("Invalid or expired OTP"));

        User user = resetToken.getUser();

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        // Invalidate all refresh tokens for this user (force re-login)
        refreshTokenRepository.revokeAllUserTokens(user.getId());

        // Send password changed confirmation email
        emailService.sendPasswordChangedEmail(user);
        log.info("Password reset successful for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public AuthenticationResponse verifyEmail(VerifyOtpRequest request) {
        log.info("Verifying email for: {}", request.getEmail());

        PasswordResetToken token = passwordResetTokenRepository
                .findValidOtpByEmailAndCodeAndPurpose(
                        request.getEmail(), request.getOtp(), LocalDateTime.now(), TokenPurpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new ValidationException("Invalid or expired verification code"));

        // Mark token as used
        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        // Set user emailVerified = true
        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("Email verified successfully for: {}", user.getEmail());

        // Generate fresh tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = createRefreshToken(user);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(user.getRoles().stream().map(r -> r.getName()).toList())
                .distributorId(user.getDistributorId())
                .merchantId(user.getMerchantId())
                .customerId(user.getCustomerId())
                .phoneNumber(user.getPhoneNumber())
                .emailVerified(true)
                .kycStatus(resolveKycStatus(user))
                .build();
    }

    @Override
    @Transactional
    public boolean resendEmailVerificationOtp(ForgotPasswordRequest request) {
        log.info("Resending email verification OTP for: {}", request.getEmail());

        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());
        if (userOptional.isEmpty()) {
            log.warn("Resend verification requested for non-existent email: {}", request.getEmail());
            return false;
        }

        User user = userOptional.get();
        if (user.isEmailVerified()) {
            log.info("Email already verified for: {}", request.getEmail());
            return true;
        }

        String otp = generateOtp();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES);

        PasswordResetToken verificationToken = PasswordResetToken.builder()
                .token(otp)
                .user(user)
                .expiresAt(expiresAt)
                .used(false)
                .purpose(TokenPurpose.EMAIL_VERIFICATION)
                .build();
        passwordResetTokenRepository.save(verificationToken);

        emailService.sendEmailVerificationOtpEmail(user, otp);
        log.info("Email verification OTP resent to: {}", user.getEmail());
        return true;
    }

    private void autoSetupGl(Distributor distributor, User user) {
        try {
            glAccountService.seedDefaultAccounts(distributor.getId(), user);
            log.info("Auto-seeded GL accounts for distributor {}", distributor.getId());
        } catch (Exception e) {
            log.warn("Failed to auto-seed GL accounts for distributor {}: {}", distributor.getId(), e.getMessage());
        }
        try {
            int year = java.time.LocalDate.now().getYear();
            for (int month = 1; month <= 12; month++) {
                glPeriodService.getOrCreate(distributor.getId(), year, month, user);
            }
            log.info("Auto-created GL periods for year {} for distributor {}", year, distributor.getId());
        } catch (Exception e) {
            log.warn("Failed to auto-create GL periods for distributor {}: {}", distributor.getId(), e.getMessage());
        }
    }

    private String generateOtp() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}
