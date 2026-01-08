package com.zuqi.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.main.Enforcer;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for Casbin-based authorization checks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CasbinAuthorizationService {

    private final Enforcer enforcer;

    /**
     * Check if a user has permission to perform an action on a resource.
     *
     * @param subject the user identifier or role
     * @param object  the resource path (e.g., "/v1/orders")
     * @param action  the HTTP method (e.g., "GET", "POST")
     * @return true if the action is allowed
     */
    public boolean enforce(String subject, String object, String action) {
        boolean allowed = enforcer.enforce(subject, object, action);
        log.debug("Casbin enforce: sub={}, obj={}, act={}, allowed={}", subject, object, action, allowed);
        return allowed;
    }

    /**
     * Check if a user (with multiple roles) has permission.
     *
     * @param roles  the user's roles
     * @param object the resource path
     * @param action the HTTP method
     * @return true if any role has permission
     */
    public boolean enforceWithRoles(List<String> roles, String object, String action) {
        for (String role : roles) {
            if (enforce(role, object, action)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add a role assignment for a user.
     *
     * @param user the user identifier
     * @param role the role to assign
     * @return true if the assignment was added
     */
    public boolean addRoleForUser(String user, String role) {
        boolean added = enforcer.addGroupingPolicy(user, role);
        if (added) {
            log.info("Added role {} for user {}", role, user);
        }
        return added;
    }

    /**
     * Remove a role assignment from a user.
     *
     * @param user the user identifier
     * @param role the role to remove
     * @return true if the assignment was removed
     */
    public boolean removeRoleForUser(String user, String role) {
        boolean removed = enforcer.removeGroupingPolicy(user, role);
        if (removed) {
            log.info("Removed role {} from user {}", role, user);
        }
        return removed;
    }

    /**
     * Get all roles for a user.
     *
     * @param user the user identifier
     * @return list of roles
     */
    public List<String> getRolesForUser(String user) {
        return enforcer.getRolesForUser(user);
    }

    /**
     * Get all users with a specific role.
     *
     * @param role the role
     * @return list of user identifiers
     */
    public List<String> getUsersForRole(String role) {
        return enforcer.getUsersForRole(role);
    }

    /**
     * Check if a user has a specific role.
     *
     * @param user the user identifier
     * @param role the role
     * @return true if the user has the role
     */
    public boolean hasRoleForUser(String user, String role) {
        return enforcer.hasRoleForUser(user, role);
    }

    /**
     * Add a policy rule.
     *
     * @param subject the subject (user or role)
     * @param object  the resource
     * @param action  the action pattern
     * @return true if the policy was added
     */
    public boolean addPolicy(String subject, String object, String action) {
        boolean added = enforcer.addPolicy(subject, object, action);
        if (added) {
            log.info("Added policy: sub={}, obj={}, act={}", subject, object, action);
        }
        return added;
    }

    /**
     * Remove a policy rule.
     *
     * @param subject the subject (user or role)
     * @param object  the resource
     * @param action  the action pattern
     * @return true if the policy was removed
     */
    public boolean removePolicy(String subject, String object, String action) {
        boolean removed = enforcer.removePolicy(subject, object, action);
        if (removed) {
            log.info("Removed policy: sub={}, obj={}, act={}", subject, object, action);
        }
        return removed;
    }

    /**
     * Get all policies for a subject.
     *
     * @param subject the subject
     * @return list of policies (each policy is a list of strings)
     */
    public List<List<String>> getPoliciesForSubject(String subject) {
        return enforcer.getFilteredPolicy(0, subject);
    }

    /**
     * Reload policies from the adapter (database or file).
     */
    public void reloadPolicy() {
        enforcer.loadPolicy();
        log.info("Reloaded Casbin policies");
    }

    /**
     * Save policies to the adapter.
     */
    public void savePolicy() {
        enforcer.savePolicy();
        log.info("Saved Casbin policies");
    }
}
