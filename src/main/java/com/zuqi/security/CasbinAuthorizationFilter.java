package com.zuqi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.domain.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter that checks Casbin authorization for each request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CasbinAuthorizationFilter extends OncePerRequestFilter {

    private final CasbinAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    // Paths that don't require authorization checks (with and without /api context)
    private static final List<String> PUBLIC_PATHS = List.of(
            "/v1/auth",
            "/api/v1/auth",
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

        // Normalize path for Casbin matching (remove /api prefix if present)
        String normalizedPath = normalizePath(path);

        // Check authorization with Casbin
        boolean authorized = authorizationService.enforceWithRoles(roles, normalizedPath, method);

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
            return user.getRoles().stream()
                    .map(role -> role.getName())
                    .toList();
        }

        // Fallback to authorities
        return authentication.getAuthorities().stream()
                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                .toList();
    }

    private String normalizePath(String path) {
        // Remove /api prefix if present
        if (path.startsWith("/api")) {
            path = path.substring(4);
        }

        // Replace UUIDs and numeric IDs with :id placeholder for matching
        // e.g., /v1/orders/123e4567-e89b-12d3-a456-426614174000 -> /v1/orders/:id
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
