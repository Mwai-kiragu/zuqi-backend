package com.zuqi.service.impl;

import com.zuqi.api.dto.auth.AuthenticationRequest;
import com.zuqi.api.dto.auth.AuthenticationResponse;
import com.zuqi.api.dto.auth.RefreshTokenRequest;
import com.zuqi.api.dto.auth.RegisterRequest;
import com.zuqi.api.dto.auth.ForgotPasswordRequest;
import com.zuqi.api.dto.auth.ResetPasswordRequest;
import com.zuqi.domain.user.PasswordResetToken;
import com.zuqi.domain.user.RefreshToken;
import com.zuqi.domain.user.Role;
import com.zuqi.domain.user.RoleName;
import com.zuqi.domain.user.User;
import com.zuqi.exception.AuthenticationException;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.PasswordResetTokenRepository;
import com.zuqi.repository.RefreshTokenRepository;
import com.zuqi.repository.RoleRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.security.JwtService;
import com.zuqi.service.AuthenticationService;
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

/**
 * Implementation of the authentication service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // Password reset token expires in 1 hour
    private static final int RESET_TOKEN_EXPIRY_HOURS = 1;

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

        // Get default role (MERCHANT for self-registration)
        Set<Role> roles = new HashSet<>();
        Role defaultRole = roleRepository.findByName(RoleName.MERCHANT)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "MERCHANT"));
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

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(user.getRoles().stream().map(r -> r.getName()).toList())
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
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        log.debug("Logging out user");

        refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
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
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Processing forgot password request for email: {}", request.getEmail());

        // Find user by email (don't reveal if user exists or not for security)
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

        if (userOptional.isEmpty()) {
            // Log but don't throw exception to prevent email enumeration
            log.warn("Forgot password requested for non-existent email: {}", request.getEmail());
            return;
        }

        User user = userOptional.get();

        // Check if user is active
        if (!user.isActive()) {
            log.warn("Forgot password requested for inactive user: {}", request.getEmail());
            return;
        }

        // Generate reset token
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(RESET_TOKEN_EXPIRY_HOURS);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);

        // In production, send email with reset link here
        // For now, just log the token (in production, this would be sent via email)
        log.info("Password reset token generated for user {}: {}", user.getEmail(), token);
        log.info("Reset link would be: {}/reset-password?token={}", "http://localhost:3000", token);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Processing password reset request");

        // Find and validate token
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findValidToken(request.getToken(), LocalDateTime.now())
                .orElseThrow(() -> new ValidationException("Invalid or expired reset token"));

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

        log.info("Password reset successful for user: {}", user.getEmail());
    }
}
