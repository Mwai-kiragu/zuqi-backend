package com.zuqi.util;

import com.zuqi.domain.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Utility class for security-related operations.
 * Provides methods to check user roles and get current user context.
 */
@Component
public class SecurityUtils {

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN = "ADMIN";

    /**
     * Get the currently authenticated user.
     *
     * @return the current User or null if not authenticated
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * Get the currently authenticated user's ID.
     *
     * @return the current user's ID or null if not authenticated
     */
    public UUID getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    /**
     * Check if the current user has SUPER_ADMIN role.
     *
     * @return true if user is SUPER_ADMIN
     */
    public boolean isSuperAdmin() {
        User user = getCurrentUser();
        return user != null && hasRole(user, ROLE_SUPER_ADMIN);
    }

    /**
     * Check if the current user has ADMIN role.
     *
     * @return true if user is ADMIN
     */
    public boolean isAdmin() {
        User user = getCurrentUser();
        return user != null && hasRole(user, ROLE_ADMIN);
    }

    /**
     * Check if the current user has SUPER_ADMIN or ADMIN role.
     *
     * @return true if user is SUPER_ADMIN or ADMIN
     */
    public boolean isSuperAdminOrAdmin() {
        return isSuperAdmin() || isAdmin();
    }

    /**
     * Check if the current user can access all distributors' data.
     * SUPER_ADMIN and ADMIN can see all data.
     *
     * @return true if user can access all data
     */
    public boolean canAccessAllData() {
        return isSuperAdminOrAdmin();
    }

    /**
     * Get the distributor ID for data filtering.
     * Returns null for SUPER_ADMIN/ADMIN (meaning no filter needed).
     * Returns the user's distributor ID for other roles.
     *
     * @return distributor ID to filter by, or null for full access
     */
    public UUID getDistributorIdForFiltering() {
        if (canAccessAllData()) {
            return null; // No filtering for SUPER_ADMIN/ADMIN
        }
        User user = getCurrentUser();
        return user != null ? user.getDistributorId() : null;
    }

    /**
     * Get the current user's distributor ID (actual value, not for filtering).
     *
     * @return the user's distributor ID
     */
    public UUID getCurrentUserDistributorId() {
        User user = getCurrentUser();
        return user != null ? user.getDistributorId() : null;
    }

    /**
     * Get the current user's merchant ID.
     *
     * @return the user's merchant ID
     */
    public UUID getCurrentUserMerchantId() {
        User user = getCurrentUser();
        return user != null ? user.getMerchantId() : null;
    }

    /**
     * Check if a user has a specific role.
     *
     * @param user the user to check
     * @param roleName the role name to check for
     * @return true if user has the role
     */
    public boolean hasRole(User user, String roleName) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(role -> role.getName().equals(roleName));
    }

    /**
     * Check if the current user has a specific role.
     *
     * @param roleName the role name to check for
     * @return true if current user has the role
     */
    public boolean currentUserHasRole(String roleName) {
        return hasRole(getCurrentUser(), roleName);
    }

    /**
     * Check if current user can access a specific distributor's data.
     *
     * @param distributorId the distributor ID to check access for
     * @return true if user can access the distributor's data
     */
    public boolean canAccessDistributor(UUID distributorId) {
        // SUPER_ADMIN and ADMIN can access all distributors
        if (canAccessAllData()) {
            return true;
        }

        // Other users can only access their own distributor
        UUID userDistributorId = getCurrentUserDistributorId();
        return userDistributorId != null && userDistributorId.equals(distributorId);
    }
}
