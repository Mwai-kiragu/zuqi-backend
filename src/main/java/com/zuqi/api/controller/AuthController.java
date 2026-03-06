package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.auth.AuthenticationRequest;
import com.zuqi.api.dto.auth.AuthenticationResponse;
import com.zuqi.api.dto.auth.RefreshTokenRequest;
import com.zuqi.api.dto.auth.RegisterRequest;
import com.zuqi.api.dto.auth.DistributorRegisterRequest;
import com.zuqi.api.dto.auth.MerchantRegisterRequest;
import com.zuqi.api.dto.auth.ForgotPasswordRequest;
import com.zuqi.api.dto.auth.ResetPasswordRequest;
import com.zuqi.api.dto.auth.VerifyOtpRequest;
import com.zuqi.api.dto.branch.SwitchBranchRequest;
import com.zuqi.api.dto.branch.SwitchBranchResponse;
import com.zuqi.service.AuthenticationService;
import com.zuqi.service.BranchService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final BranchService branchService;
    private final SecurityUtils securityUtils;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns authentication tokens")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthenticationResponse response = authenticationService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    @PostMapping("/register/distributor")
    @Operation(summary = "Register a new distributor", description = "Creates a new distributor, admin user, and FREE_TRIAL subscription")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> registerDistributor(
            @Valid @RequestBody DistributorRegisterRequest request) {
        AuthenticationResponse response = authenticationService.registerDistributor(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Distributor registration successful", response));
    }

    @PostMapping("/register/merchant")
    @Operation(summary = "Register a new merchant brand", description = "Creates a Merchant brand, default Distributor, MERCHANT_ADMIN user, and FREE_TRIAL subscription")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> registerMerchant(
            @Valid @RequestBody MerchantRegisterRequest request) {
        AuthenticationResponse response = authenticationService.registerMerchant(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Merchant brand registration successful", response));
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates user and returns access and refresh tokens")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> login(
            @Valid @RequestBody AuthenticationRequest request) {
        AuthenticationResponse response = authenticationService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generates new access token using refresh token")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthenticationResponse response = authenticationService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Invalidates the refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Sends OTP to the user's email for password reset")
    public ResponseEntity<ApiResponse<Boolean>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        boolean userExists = authenticationService.forgotPassword(request);
        if (userExists) {
            return ResponseEntity.ok(ApiResponse.success("OTP sent to your email. Please check your inbox.", true));
        } else {
            return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                    .success(false)
                    .message("No account found with this email address")
                    .data(false)
                    .build());
        }
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verifies the OTP before allowing password reset")
    public ResponseEntity<ApiResponse<Boolean>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        boolean isValid = authenticationService.verifyOtp(request);
        if (isValid) {
            return ResponseEntity.ok(ApiResponse.success("OTP verified successfully", true));
        } else {
            return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                    .success(false)
                    .message("Invalid or expired OTP")
                    .data(false)
                    .build());
        }
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Resets the user's password using email and OTP")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successful. You can now login with your new password."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email", description = "Verifies user email using 6-digit OTP")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> verifyEmail(
            @Valid @RequestBody VerifyOtpRequest request) {
        AuthenticationResponse response = authenticationService.verifyEmail(request);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", response));
    }

    @PostMapping("/switch-branch")
    @Operation(summary = "Switch active branch", description = "Returns a new JWT token containing the selected branchId")
    public ResponseEntity<ApiResponse<SwitchBranchResponse>> switchBranch(
            @Valid @RequestBody SwitchBranchRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        SwitchBranchResponse response = branchService.switchBranch(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Branch switched successfully", response));
    }

    @PostMapping("/resend-verification-otp")
    @Operation(summary = "Resend verification OTP", description = "Resends email verification OTP")
    public ResponseEntity<ApiResponse<Boolean>> resendVerificationOtp(
            @Valid @RequestBody ForgotPasswordRequest request) {
        boolean sent = authenticationService.resendEmailVerificationOtp(request);
        if (sent) {
            return ResponseEntity.ok(ApiResponse.success("Verification code sent to your email.", true));
        } else {
            return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                    .success(false)
                    .message("No account found with this email address")
                    .data(false)
                    .build());
        }
    }
}
