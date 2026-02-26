package com.zuqi.util;

import com.zuqi.domain.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityUtils {

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }
        return null;
    }

    public UUID getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    public boolean isSuperAdmin() {
        User user = getCurrentUser();
        return user != null && hasRole(user, ROLE_SUPER_ADMIN);
    }

    public boolean canAccessAllData() {
        return isSuperAdmin();
    }

    public UUID getDistributorIdForFiltering() {
        if (canAccessAllData()) {
            return null;
        }
        User user = getCurrentUser();
        return user != null ? user.getDistributorId() : null;
    }

    public UUID getCurrentUserDistributorId() {
        User user = getCurrentUser();
        return user != null ? user.getDistributorId() : null;
    }

    public UUID getCurrentUserMerchantId() {
        User user = getCurrentUser();
        return user != null ? user.getMerchantId() : null;
    }

    public boolean hasRole(User user, String roleName) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(role -> role.getName().equals(roleName));
    }

    public boolean currentUserHasRole(String roleName) {
        return hasRole(getCurrentUser(), roleName);
    }

    public boolean canAccessDistributor(UUID distributorId) {
        // SUPER_ADMIN can access all distributors
        if (canAccessAllData()) {
            return true;
        }

        // Other users can only access their own distributor
        UUID userDistributorId = getCurrentUserDistributorId();
        return userDistributorId != null && userDistributorId.equals(distributorId);
    }
}
