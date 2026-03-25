package com.zuqi.security;

import org.casbin.jcasbin.main.Enforcer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CasbinAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(CasbinAuthorizationService.class);

    private final Enforcer enforcer;

    public CasbinAuthorizationService(Enforcer enforcer) {
        this.enforcer = enforcer;
    }

    public boolean enforce(String subject, String object, String action) {
        boolean allowed = enforcer.enforce(subject, object, action);
        if (!allowed) {
            log.warn("Casbin DENIED: sub={}, obj={}, act={}", subject, object, action);
            // Log available policies for this subject
            var policies = enforcer.getFilteredPolicy(0, subject);
            log.warn("Available policies for {}: {}", subject, policies);
        } else {
            log.debug("Casbin enforce: sub={}, obj={}, act={}, allowed={}", subject, object, action, allowed);
        }
        return allowed;
    }

    public boolean enforceWithRoles(List<String> roles, String object, String action) {
        for (String role : roles) {
            if (enforce(role, object, action)) {
                return true;
            }
        }
        return false;
    }

    public boolean addRoleForUser(String user, String role) {
        boolean added = enforcer.addGroupingPolicy(user, role);
        if (added) {
            log.info("Added role {} for user {}", role, user);
        }
        return added;
    }

    public boolean removeRoleForUser(String user, String role) {
        boolean removed = enforcer.removeGroupingPolicy(user, role);
        if (removed) {
            log.info("Removed role {} from user {}", role, user);
        }
        return removed;
    }

    public List<String> getRolesForUser(String user) {
        return enforcer.getRolesForUser(user);
    }

    public List<String> getUsersForRole(String role) {
        return enforcer.getUsersForRole(role);
    }

    public boolean hasRoleForUser(String user, String role) {
        return enforcer.hasRoleForUser(user, role);
    }

    public boolean addPolicy(String subject, String object, String action) {
        boolean added = enforcer.addPolicy(subject, object, action);
        if (added) {
            log.info("Added policy: sub={}, obj={}, act={}", subject, object, action);
        }
        return added;
    }

    public boolean removePolicy(String subject, String object, String action) {
        boolean removed = enforcer.removePolicy(subject, object, action);
        if (removed) {
            log.info("Removed policy: sub={}, obj={}, act={}", subject, object, action);
        }
        return removed;
    }

    public List<List<String>> getPoliciesForSubject(String subject) {
        return enforcer.getFilteredPolicy(0, subject);
    }

    public void reloadPolicy() {
        enforcer.loadPolicy();
        log.info("Reloaded Casbin policies");
    }

    public void savePolicy() {
        enforcer.savePolicy();
        log.info("Saved Casbin policies");
    }
}
