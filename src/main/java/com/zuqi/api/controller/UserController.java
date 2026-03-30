package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.common.DeactivateRequest;
import com.zuqi.api.dto.user.ChangePasswordRequest;
import com.zuqi.api.dto.user.CreateUserRequest;
import com.zuqi.api.dto.user.DisableTwoFactorRequest;
import com.zuqi.api.dto.user.EnableTwoFactorRequest;
import com.zuqi.api.dto.user.EnableTwoFactorResponse;
import com.zuqi.api.dto.user.NotificationSettingsRequest;
import com.zuqi.api.dto.user.NotificationSettingsResponse;
import com.zuqi.api.dto.user.ResetPasswordRequest;
import com.zuqi.api.dto.user.SecuritySettingsResponse;
import com.zuqi.api.dto.user.UpdateProfileRequest;
import com.zuqi.api.dto.user.UpdateUserRequest;
import com.zuqi.api.dto.user.UserResponse;
import com.zuqi.api.dto.user.VerifyTwoFactorRequest;
import com.zuqi.domain.user.User;
import com.zuqi.service.TwoFactorAuthService;
import com.zuqi.service.UserService;
import com.zuqi.service.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management APIs")
public class UserController {

    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final TwoFactorAuthService twoFactorAuthService;

    @GetMapping
    @Operation(summary = "Get all users (ADMIN only)")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {

        Page<UserResponse> users = userService.getAllUsers(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")), active, search);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/distributor/{distributorId}")
    @Operation(summary = "Get users by distributor")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsersByDistributor(
            @PathVariable UUID distributorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {

        Page<UserResponse> users = userService.getUsersByDistributor(distributorId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")), active, search);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/role/{role}")
    @Operation(summary = "Get users by role")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsersByRole(
            @PathVariable String role,
            @RequestParam(required = false) UUID distributorId) {

        List<UserResponse> users = userService.getUsersByRole(role, distributorId);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/roles")
    @Operation(summary = "Get available roles")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableRoles(
            @AuthenticationPrincipal User currentUser) {

        boolean isSuperAdmin = false;
        if (currentUser != null && currentUser.getRoles() != null) {
            isSuperAdmin = currentUser.getRoles().stream()
                    .anyMatch(r -> r.getName().equals("SUPER_ADMIN"));
        }

        List<String> roles = userService.getAvailableRoles(isSuperAdmin);
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved successfully", roles));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal User currentUser) {

        UserResponse user = userService.getUserById(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved successfully", user));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserResponse user = userService.updateProfile(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", user));
    }

    @PostMapping("/me/change-password")
    @Operation(summary = "Change current user password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    @GetMapping("/me/settings/notifications")
    @Operation(summary = "Get current user notification settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getNotificationSettings(
            @AuthenticationPrincipal User currentUser) {

        NotificationSettingsResponse settings = userSettingsService.getNotificationSettings(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Notification settings retrieved successfully", settings));
    }

    @PutMapping("/me/settings/notifications")
    @Operation(summary = "Update current user notification settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody NotificationSettingsRequest request) {

        NotificationSettingsResponse settings = userSettingsService.updateNotificationSettings(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Notification settings updated successfully", settings));
    }

    @GetMapping("/me/settings/security")
    @Operation(summary = "Get current user security settings")
    public ResponseEntity<ApiResponse<SecuritySettingsResponse>> getSecuritySettings(
            @AuthenticationPrincipal User currentUser) {

        SecuritySettingsResponse settings = userSettingsService.getSecuritySettings(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Security settings retrieved successfully", settings));
    }

    @PostMapping("/me/two-factor/enable")
    @Operation(summary = "Initialize two-factor authentication setup")
    public ResponseEntity<ApiResponse<EnableTwoFactorResponse>> enableTwoFactor(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody EnableTwoFactorRequest request) {

        EnableTwoFactorResponse response = twoFactorAuthService.initializeTwoFactor(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Two-factor authentication initialized", response));
    }

    @PostMapping("/me/two-factor/verify")
    @Operation(summary = "Verify and complete two-factor authentication setup")
    public ResponseEntity<ApiResponse<Void>> verifyTwoFactor(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody VerifyTwoFactorRequest request) {

        boolean verified = twoFactorAuthService.verifyAndEnable(currentUser.getId(), request.getCode());
        if (verified) {
            return ResponseEntity.ok(ApiResponse.success("Two-factor authentication enabled successfully"));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid verification code"));
    }

    @PostMapping("/me/two-factor/disable")
    @Operation(summary = "Disable two-factor authentication")
    public ResponseEntity<ApiResponse<Void>> disableTwoFactor(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody DisableTwoFactorRequest request) {

        twoFactorAuthService.disableTwoFactor(currentUser.getId(), request.getCurrentPassword());
        return ResponseEntity.ok(ApiResponse.success("Two-factor authentication disabled successfully"));
    }

    @PostMapping("/me/two-factor/backup-codes/regenerate")
    @Operation(summary = "Regenerate backup codes for two-factor authentication")
    public ResponseEntity<ApiResponse<java.util.List<String>>> regenerateBackupCodes(
            @AuthenticationPrincipal User currentUser) {

        java.util.List<String> backupCodes = twoFactorAuthService.regenerateBackupCodes(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Backup codes regenerated successfully", backupCodes));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));
    }

    @PostMapping
    @Operation(summary = "Create a new user")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal User currentUser) {

        UUID creatorDistributorId = currentUser.getDistributorId();
        UserResponse user = userService.createUser(request, creatorDistributorId);
        return ResponseEntity.ok(ApiResponse.success("User created successfully", user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {

        UserResponse user = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", user));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Reset user password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request) {

        userService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a user with reason")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(
            @PathVariable UUID id,
            @Valid @RequestBody DeactivateRequest request,
            @AuthenticationPrincipal User currentUser) {
        userService.deactivateUser(id, request.getReason(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully"));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a user")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable UUID id) {
        userService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User activated successfully"));
    }
}
