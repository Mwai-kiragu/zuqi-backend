package com.zuqi.service;

import com.zuqi.api.dto.auth.AuthenticationRequest;
import com.zuqi.api.dto.auth.AuthenticationResponse;
import com.zuqi.api.dto.auth.RegisterRequest;
import com.zuqi.api.dto.auth.RefreshTokenRequest;
import com.zuqi.api.dto.auth.ForgotPasswordRequest;
import com.zuqi.api.dto.auth.ResetPasswordRequest;
import com.zuqi.api.dto.auth.VerifyOtpRequest;

public interface AuthenticationService {

    AuthenticationResponse register(RegisterRequest request);

    AuthenticationResponse authenticate(AuthenticationRequest request);

    AuthenticationResponse refreshToken(RefreshTokenRequest request);

    void logout(String refreshToken);

    boolean forgotPassword(ForgotPasswordRequest request);

    boolean verifyOtp(VerifyOtpRequest request);

    void resetPassword(ResetPasswordRequest request);
}
