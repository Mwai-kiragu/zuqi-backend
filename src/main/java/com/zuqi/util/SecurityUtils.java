package com.zuqi.util;

import com.zuqi.domain.accesscontrol.UserTypePermission;
import com.zuqi.domain.user.User;
import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final DistributorRepository distributorRepository;

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

    /**
     * Returns the effective merchant ID for the current user.
     * - MERCHANT_ADMIN: directly from user.merchantId
     * - DISTRIBUTOR_ADMIN / other distributor staff: resolved through distributor → merchant FK
     * - Returns null for SUPER_ADMIN (they must supply merchantId explicitly)
     */
    public UUID getEffectiveMerchantId() {
        User user = getCurrentUser();
        if (user == null) return null;
        // Direct merchant link (MERCHANT_ADMIN)
        if (user.getMerchantId() != null) return user.getMerchantId();
        // Resolve through distributor
        if (user.getDistributorId() != null) {
            return distributorRepository.findById(user.getDistributorId())
                    .map(d -> d.getMerchant() != null ? d.getMerchant().getId() : null)
                    .orElse(null);
        }
        return null;
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

    /**
     * Returns the workflow tier from the user's group (INITIATOR / VERIFIER / AUTHORIZER),
     * or null if the user has no group or the group has no workflow tier.
     * Also falls back to the legacy role-based tier for backwards compatibility.
     */
    public String getCurrentUserWorkflowTier() {
        User user = getCurrentUser();
        if (user == null) return null;
        // UserGroup-based tier (new system)
        if (user.getUserGroup() != null && user.getUserGroup().getWorkflowTier() != null) {
            return user.getUserGroup().getWorkflowTier();
        }
        // Legacy role-based tier (for existing INITIATOR/VERIFIER/AUTHORIZER role users)
        for (String tier : List.of("INITIATOR", "VERIFIER", "AUTHORIZER")) {
            if (hasRole(user, tier)) return tier;
        }
        return null;
    }

    /**
     * Returns true if the current user's workflow tier matches the given tier.
     */
    public boolean currentUserHasWorkflowTier(String tier) {
        if (tier == null) return false;
        String stored = getCurrentUserWorkflowTier();
        if (stored == null) return false;
        // Support comma-separated multi-tier values (e.g. "INITIATOR,VERIFIER")
        for (String t : stored.split(",")) {
            if (tier.equals(t.trim())) return true;
        }
        return false;
    }

    /**
     * Returns true if the current user can perform the given action on the given module.
     * System-level admins (SUPER_ADMIN, DISTRIBUTOR_ADMIN, MERCHANT_ADMIN) always have access.
     * Staff users are checked against their UserType permissions.
     */
    public boolean currentUserHasModulePermission(String module, String action) {
        User user = getCurrentUser();
        if (user == null) return false;
        // System admins bypass module permissions
        for (String adminRole : List.of("SUPER_ADMIN", "DISTRIBUTOR_ADMIN", "MERCHANT_ADMIN")) {
            if (hasRole(user, adminRole)) return true;
        }
        // Check UserType permissions
        if (user.getUserGroup() == null || user.getUserGroup().getUserType() == null) return false;
        return user.getUserGroup().getUserType().getPermissions().stream()
                .filter(p -> p.getModule().equalsIgnoreCase(module))
                .findFirst()
                .map(p -> switch (action.toUpperCase()) {
                    case "CREATE" -> p.isCanCreate();
                    case "READ"   -> p.isCanRead();
                    case "UPDATE" -> p.isCanUpdate();
                    case "DELETE" -> p.isCanDelete();
                    case "APPROVE" -> p.isCanApprove();
                    default -> false;
                })
                .orElse(false);
    }

    /**
     * Returns the active branch UUID from the JWT, or null if no branch context.
     */
    public UUID getCurrentBranchId() {
        User user = getCurrentUser();
        return user != null ? user.getActiveBranchId() : null;
    }

    /**
     * Returns the effective branch filter to use in queries:
     * - null  →  no branch filter (show all branches) for SUPER_ADMIN, HQ branch, or no branch selected
     * - UUID  →  filter to this specific branch
     *
     * Business rule: the HQ branch represents the whole business, so it sees everything.
     */
    public UUID getEffectiveBranchId() {
        if (canAccessAllData()) {
            return null; // SUPER_ADMIN always sees everything
        }
        User user = getCurrentUser();
        if (user == null) {
            return null;
        }
        if (user.getActiveBranchId() == null) {
            return null; // No branch selected yet — show all (pre-switch state)
        }
        if (user.isActiveBranchHeadquarters()) {
            return null; // HQ branch sees all branches
        }
        return user.getActiveBranchId(); // specific non-HQ branch
    }
}
