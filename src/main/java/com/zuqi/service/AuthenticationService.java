package com.zuqi.service;

import com.zuqi.api.dto.auth.AuthenticationRequest;
import com.zuqi.api.dto.auth.AuthenticationResponse;
import com.zuqi.api.dto.auth.RegisterRequest;
import com.zuqi.api.dto.auth.RefreshTokenRequest;
import com.zuqi.api.dto.auth.ForgotPasswordRequest;
import com.zuqi.api.dto.auth.ResetPasswordRequest;

/**
 * Service interface for authentication operations.
 */
public interface AuthenticationService {

    /**
     * Registers a new user in the system.
     *
     * @param request the registration request containing user details
     * @return authentication response with tokens
     */
    AuthenticationResponse register(RegisterRequest request);

    /**
     * Authenticates a user and generates tokens.
     *
     * @param request the authentication request with credentials
     * @return authentication response with tokens
     */
    AuthenticationResponse authenticate(AuthenticationRequest request);

    /**
     * Refreshes the access token using a valid refresh token.
     *
     * @param request the refresh token request
     * @return authentication response with new tokens
     */
    AuthenticationResponse refreshToken(RefreshTokenRequest request);

    /**
     * Logs out a user by invalidating their refresh token.
     *
     * @param refreshToken the refresh token to invalidate
     */
    void logout(String refreshToken);

    /**
     * Initiates the forgot password flow by generating a reset token.
     *
     * @param request the forgot password request containing email
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Resets the user's password using a valid reset token.
     *
     * @param request the reset password request containing token and new password
     */
    void resetPassword(ResetPasswordRequest request);
}
