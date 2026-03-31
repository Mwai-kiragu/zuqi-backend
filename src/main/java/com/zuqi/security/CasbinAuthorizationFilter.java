package com.zuqi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.zuqi.domain.accesscontrol.UserTypePermission;
import com.zuqi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CasbinAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CasbinAuthorizationFilter.class);

    private final CasbinAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public CasbinAuthorizationFilter(CasbinAuthorizationService authorizationService,
                                     ObjectMapper objectMapper,
                                     UserRepository userRepository) {
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    /**
     * Maps UserType module names → URL path prefixes (after /api normalization).
     * canRead  → GET allowed
     * canCreate→ POST allowed
     * canUpdate→ PUT/PATCH allowed
     * canDelete→ DELETE allowed
     * canApprove→ POST on approve/process/reject sub-paths allowed
     *
     * Keys are UPPERCASE canonical names. Lookup is done via resolveModulePaths()
     * which also handles legacy lowercase/singular names from old DB rows.
     */
    private static final Map<String, List<String>> MODULE_PATHS = Map.ofEntries(
        Map.entry("DASHBOARD",          List.of("/v1/dashboard")),
        Map.entry("BRANCHES",           List.of("/v1/branches")),
        Map.entry("WAREHOUSES",         List.of("/v1/inventory/warehouses")),
        Map.entry("INVENTORY",          List.of("/v1/inventory")),
        Map.entry("ORDERS",             List.of("/v1/orders")),
        Map.entry("INVOICES",           List.of("/v1/invoices")),
        Map.entry("CUSTOMERS",          List.of("/v1/customers")),
        Map.entry("PRODUCTS",           List.of("/v1/products")),
        Map.entry("SUPPLIERS",          List.of("/v1/suppliers")),
        Map.entry("PROCUREMENT",        List.of("/v1/purchase-orders", "/v1/purchase-requisitions", "/v1/grns")),
        Map.entry("SUPPLIER_BILLS",     List.of("/v1/supplier-bills")),
        Map.entry("FUNDS_TRANSFERS",    List.of("/v1/funds-transfers")),
        Map.entry("PAYMENTS",           List.of("/v1/payments")),
        Map.entry("POS",                List.of("/v1/pos")),
        Map.entry("REPORTS",            List.of("/v1/reports", "/v1/gl/reports", "/v1/financial-overview")),
        Map.entry("GENERAL_LEDGER",     List.of("/v1/reports", "/v1/gl/reports", "/v1/financial-overview")),
        Map.entry("GL_REPORTS",         List.of("/v1/gl/reports")),
        Map.entry("BUDGETS",            List.of("/v1/gl/budgets")),
        Map.entry("CHART_OF_ACCOUNTS",  List.of("/v1/gl/accounts")),
        Map.entry("JOURNAL_ENTRIES",    List.of("/v1/gl/journals")),
        Map.entry("COST_CENTERS",       List.of("/v1/gl/cost-centers")),
        Map.entry("GL_PERIODS",         List.of("/v1/gl/periods")),
        Map.entry("BANK_RECONCILIATION",List.of("/v1/accounting/bank-reconciliations")),
        Map.entry("TAX_RATES",          List.of("/v1/accounting/tax-rates")),
        Map.entry("STOCK_TRANSFERS",    List.of("/v1/inventory/transfers")),
        Map.entry("STOCK_TAKES",        List.of("/v1/inventory/stock-takes")),
        Map.entry("DISTRIBUTORS",       List.of("/v1/distributors")),
        Map.entry("PROMOTIONS",         List.of("/v1/promotions")),
        Map.entry("PRICE_LISTS",        List.of("/v1/price-lists")),
        Map.entry("SALES_TEAM",         List.of("/v1/users")),
        Map.entry("PROFILE",            List.of("/v1/users/me")),
        Map.entry("USERS",              List.of("/v1/users")),
        Map.entry("CRM",                List.of("/v1/crm")),
        Map.entry("EXPENSES",           List.of("/v1/expenses")),
        Map.entry("CREDIT",             List.of("/v1/credit")),
        Map.entry("AR_AGING",           List.of("/v1/reports/ar-aging")),
        Map.entry("AP_AGING",           List.of("/v1/reports/ap-aging")),
        Map.entry("PAYMENT_SETUP",      List.of("/v1/payment-setup", "/v1/mpesa", "/v1/kcb")),
        Map.entry("AUDIT_LOGS",         List.of("/v1/audit-logs")),
        Map.entry("ADMIN",              List.of("/v1/users", "/v1/user-groups", "/v1/user-types",
                                               "/v1/roles", "/v1/access-control")),
        Map.entry("APPROVALS",          List.of("/v1/approvals")),
        Map.entry("AUDIT",              List.of("/v1/audit-logs")),
        Map.entry("BILLING",            List.of("/v1/billing")),
        Map.entry("ROLES",              List.of("/v1/roles"))
    );

    /**
     * Maps legacy lowercase/singular module names (from old permission rows) to their
     * canonical uppercase MODULE_PATHS key. Handles DB entries created before the naming
     * was standardised to UPPERCASE_PLURAL.
     */
    private static final Map<String, String> LEGACY_MODULE_ALIASES = Map.ofEntries(
        Map.entry("order",          "ORDERS"),
        Map.entry("user",           "USERS"),
        Map.entry("payment",        "PAYMENTS"),
        Map.entry("inventory",      "INVENTORY"),
        Map.entry("merchant",       "CUSTOMERS"),
        Map.entry("report",         "REPORTS"),
        Map.entry("settings",       "ADMIN"),
        Map.entry("credit",         "CREDIT"),
        Map.entry("invoice",        "INVOICES"),
        Map.entry("product",        "PRODUCTS"),
        Map.entry("supplier",       "SUPPLIERS"),
        Map.entry("customer",       "CUSTOMERS"),
        Map.entry("warehouse",      "WAREHOUSES"),
        Map.entry("branch",         "BRANCHES"),
        Map.entry("procurement",    "PROCUREMENT"),
        Map.entry("approval",       "APPROVALS"),
        Map.entry("audit",          "AUDIT"),
        Map.entry("expense",        "EXPENSES"),
        Map.entry("dashboard",      "DASHBOARD")
    );

    /** Resolve a UserTypePermission module name to its MODULE_PATHS entry, tolerating case variants and legacy names. */
    private static List<String> resolveModulePaths(String module) {
        if (module == null) return null;
        // 1. Exact match (canonical uppercase)
        List<String> paths = MODULE_PATHS.get(module);
        if (paths != null) return paths;
        // 2. Uppercase conversion (e.g. "orders" → "ORDERS")
        paths = MODULE_PATHS.get(module.toUpperCase());
        if (paths != null) return paths;
        // 3. Legacy alias (e.g. "order" → "ORDERS", "merchant" → "CUSTOMERS")
        String canonical = LEGACY_MODULE_ALIASES.get(module.toLowerCase());
        if (canonical != null) return MODULE_PATHS.get(canonical);
        return null;
    }

    private static final Set<String> APPROVE_SUFFIXES = Set.of(
        "/approve", "/process", "/reject", "/cancel", "/disburse", "/receive",
        "/confirm", "/dispatch", "/activate", "/deactivate"
    );

    // Paths that don't require authorization checks (with and without /api context)
    private static final List<String> PUBLIC_PATHS = List.of(
            "/v1/auth",
            "/api/v1/auth",
            "/v1/invoices/public",
            "/api/v1/invoices/public",
            "/v1/payments/methods",
            "/api/v1/payments/methods",
            "/v1/mpesa/callback",
            "/api/v1/mpesa/callback",
            "/v1/kcb/callback",
            "/api/v1/kcb/callback",
            "/uploads",
            "/api/uploads",
            "/v3/api-docs",
            "/api/v3/api-docs",
            "/swagger-ui",
            "/api/swagger-ui",
            "/actuator/health",
            "/api/actuator/health",
            "/actuator/info",
            "/api/actuator/info"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip authorization for public paths
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip for OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get user roles
        List<String> roles = getUserRoles(authentication);
        log.debug("User {} has roles: {}", authentication.getName(), roles);

        // SUPER_ADMIN bypass - always allow full access
        if (roles.contains("SUPER_ADMIN")) {
            log.debug("Super admin bypass: user={}, roles={}, path={}, method={}",
                    authentication.getName(), roles, path, method);
            filterChain.doFilter(request, response);
            return;
        }

        // Normalize path for Casbin matching (remove /api prefix if present)
        String normalizedPath = normalizePath(path);

        log.debug("Authorization check: user={}, roles={}, normalizedPath={}, method={}",
                authentication.getName(), roles, normalizedPath, method);

        // Check authorization with Casbin
        boolean authorized = authorizationService.enforceWithRoles(roles, normalizedPath, method);

        // If Casbin denies, fall back to UserType module permissions
        if (!authorized && authentication.getPrincipal() instanceof User user) {
            authorized = isAllowedByUserType(user, normalizedPath, method);
            if (authorized) {
                log.debug("UserType permission granted: user={}, path={}, method={}",
                        authentication.getName(), normalizedPath, method);
            }
        }

        if (!authorized) {
            log.warn("Access denied: user={}, roles={}, path={}, method={}",
                    authentication.getName(), roles, normalizedPath, method);
            sendForbiddenResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private List<String> getUserRoles(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof User user) {
            return user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(auth -> auth.replace("ROLE_", ""))
                    .toList();
        }

        return authentication.getAuthorities().stream()
                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                .toList();
    }

    /**
     * Check whether the user's UserType grants them access to this path/method.
     * Uses a direct repository query to avoid LazyInitializationException.
     * canRead   → GET
     * canCreate → POST (non-approve)
     * canUpdate → PUT, PATCH
     * canDelete → DELETE
     * canApprove→ POST on approve/process/reject/etc sub-paths
     */
    private boolean isAllowedByUserType(User user, String path, String method) {
        List<UserTypePermission> permissions = userRepository.findUserTypePermissionsByUserId(user.getId());
        if (permissions == null || permissions.isEmpty()) return false;

        boolean isApproveAction = "POST".equalsIgnoreCase(method)
                && APPROVE_SUFFIXES.stream().anyMatch(path::endsWith);

        for (UserTypePermission perm : permissions) {
            List<String> prefixes = resolveModulePaths(perm.getModule());
            if (prefixes == null) continue;
            boolean matchesPath = prefixes.stream().anyMatch(prefix ->
                    path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + "?"));
            if (!matchesPath) continue;

            if ("GET".equalsIgnoreCase(method)    && perm.isCanRead())                    return true;
            if ("POST".equalsIgnoreCase(method)   && !isApproveAction && perm.isCanCreate()) return true;
            if ("PUT".equalsIgnoreCase(method)    && perm.isCanUpdate())                  return true;
            if ("PATCH".equalsIgnoreCase(method)  && perm.isCanUpdate())                  return true;
            if ("DELETE".equalsIgnoreCase(method) && perm.isCanDelete())                  return true;
            if (isApproveAction                   && perm.isCanApprove())                 return true;
        }
        return false;
    }

    private String normalizePath(String path) {
        if (path.startsWith("/api")) {
            path = path.substring(4);
        }

        path = path.replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "/:id");
        path = path.replaceAll("/\\d+", "/:id");

        return path;
    }

    private void sendForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> apiResponse = ApiResponse.error("Access denied. You don't have permission to access this resource.");
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
