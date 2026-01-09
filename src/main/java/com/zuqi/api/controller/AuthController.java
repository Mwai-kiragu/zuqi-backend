package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.auth.AuthenticationRequest;
import com.zuqi.api.dto.auth.AuthenticationResponse;
import com.zuqi.api.dto.auth.RefreshTokenRequest;
import com.zuqi.api.dto.auth.RegisterRequest;
import com.zuqi.api.dto.auth.ForgotPasswordRequest;
import com.zuqi.api.dto.auth.ResetPasswordRequest;
import com.zuqi.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;

    /**
     * Registers a new user account.
     *
     * @param request the registration request
     * @return the authentication response with tokens
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns authentication tokens")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthenticationResponse response = authenticationService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", response));
    }

    /**
     * Authenticates a user with email and password.
     *
     * @param request the authentication request
     * @return the authentication response with tokens
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates user and returns access and refresh tokens")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> login(
            @Valid @RequestBody AuthenticationRequest request) {
        AuthenticationResponse response = authenticationService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    /**
     * Refreshes the access token using a valid refresh token.
     *
     * @param request the refresh token request
     * @return the authentication response with new tokens
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generates new access token using refresh token")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthenticationResponse response = authenticationService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    /**
     * Logs out the user by invalidating the refresh token.
     *
     * @param request the refresh token to invalidate
     * @return success response
     */
    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Invalidates the refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    /**
     * Initiates the forgot password flow.
     *
     * @param request the forgot password request containing email
     * @return success response
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Sends password reset instructions to the user's email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.forgotPassword(request);
        // Always return success to prevent email enumeration
        return ResponseEntity.ok(ApiResponse.success("If your email is registered, you will receive password reset instructions shortly"));
    }

    /**
     * Resets the user's password using a valid reset token.
     *
     * @param request the reset password request containing token and new password
     * @return success response
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Resets the user's password using a valid reset token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successful. You can now login with your new password."));
    }
}
