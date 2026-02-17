package com.zuqi.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("user_id")
    private UUID userId;

    private String email;

    @JsonProperty("full_name")
    private String fullName;

    private List<String> roles;

    @JsonProperty("distributor_id")
    private UUID distributorId;

    @JsonProperty("merchant_id")
    private UUID merchantId;

    @JsonProperty("phone_number")
    private String phoneNumber;
}
