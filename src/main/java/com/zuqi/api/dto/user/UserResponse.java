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
    /** The workflow tier role if assigned (INITIATOR / VERIFIER / AUTHORIZER), null otherwise. */
    private String workflowTierRole;
    private UUID distributorId;
    private String distributorName;
    private UUID merchantId;
    private String merchantName;
    private boolean active;
    private boolean emailVerified;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private String deactivationReason;
    private LocalDateTime deactivatedAt;
    private String deactivatedByName;
    /** UserGroup id and name — drives module permissions + workflow tier */
    private UUID userGroupId;
    private String userGroupName;
    /** UserType assigned to the user's group */
    private UUID userTypeId;
    private String userTypeName;
    /** Effective workflow tier: from UserGroup if set, else from legacy role */
    private String workflowTier;

    private static final java.util.Set<String> TIER_ROLES = java.util.Set.of("INITIATOR", "VERIFIER", "AUTHORIZER");

    public static UserResponse fromEntity(User user) {
        List<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream().map(r -> r.getName()).toList()
                : List.of();

        String tierRole = roleNames.stream().filter(TIER_ROLES::contains).findFirst().orElse(null);

        // Effective workflow tier: prefer UserGroup tier over legacy role
        String effectiveTier = (user.getUserGroup() != null && user.getUserGroup().getWorkflowTier() != null)
                ? user.getUserGroup().getWorkflowTier()
                : tierRole;

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .roles(roleNames)
                .workflowTierRole(tierRole)
                .workflowTier(effectiveTier)
                .distributorId(user.getDistributorId())
                .merchantId(user.getMerchantId())
                .userGroupId(user.getUserGroup() != null ? user.getUserGroup().getId() : null)
                .userGroupName(user.getUserGroup() != null ? user.getUserGroup().getName() : null)
                .userTypeId(user.getUserGroup() != null && user.getUserGroup().getUserType() != null
                        ? user.getUserGroup().getUserType().getId() : null)
                .userTypeName(user.getUserGroup() != null && user.getUserGroup().getUserType() != null
                        ? user.getUserGroup().getUserType().getName() : null)
                .active(user.isActive())
                .emailVerified(user.isEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .deactivationReason(user.getDeactivationReason())
                .deactivatedAt(user.getDeactivatedAt())
                .deactivatedByName(user.getDeactivatedBy() != null ? user.getDeactivatedBy().getFullName() : null)
                .build();
    }

    public static UserResponse fromEntityWithNames(User user, String distributorName, String merchantName) {
        UserResponse response = fromEntity(user);
        response.setDistributorName(distributorName);
        response.setMerchantName(merchantName);
        return response;
    }
}
