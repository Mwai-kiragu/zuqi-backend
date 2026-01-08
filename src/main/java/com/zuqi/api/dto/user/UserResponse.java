package com.zuqi.api.dto.user;

import com.zuqi.domain.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private List<String> roles;
    private UUID distributorId;
    private String distributorName;
    private UUID merchantId;
    private String merchantName;
    private boolean active;
    private boolean emailVerified;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    public static UserResponse fromEntity(User user) {
        List<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream().map(r -> r.getName()).toList()
                : List.of();

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .roles(roleNames)
                .distributorId(user.getDistributorId())
                .merchantId(user.getMerchantId())
                .active(user.isActive())
                .emailVerified(user.isEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public static UserResponse fromEntityWithNames(User user, String distributorName, String merchantName) {
        UserResponse response = fromEntity(user);
        response.setDistributorName(distributorName);
        response.setMerchantName(merchantName);
        return response;
    }
}
