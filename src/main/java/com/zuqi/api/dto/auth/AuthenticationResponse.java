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

    @JsonProperty("customer_id")
    private UUID customerId;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("email_verified")
    private boolean emailVerified;

    @JsonProperty("kyc_status")
    private String kycStatus;

    @JsonProperty("must_change_password")
    private boolean mustChangePassword;

    /** Effective workflow tier: from UserGroup if set, else from legacy role (INITIATOR/VERIFIER/AUTHORIZER). */
    @JsonProperty("workflow_tier")
    private String workflowTier;

    @JsonProperty("user_group_id")
    private UUID userGroupId;

    @JsonProperty("user_group_name")
    private String userGroupName;

    @JsonProperty("distributor_name")
    private String distributorName;

    @JsonProperty("merchant_name")
    private String merchantName;
}
