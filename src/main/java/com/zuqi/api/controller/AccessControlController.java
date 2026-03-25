package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.accesscontrol.*;
import com.zuqi.service.AccessControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Access Control", description = "Manage UserTypes and UserGroups")
public class AccessControlController {

    private final AccessControlService accessControlService;

    // ─── UserTypes ────────────────────────────────────────────────────────────

    @GetMapping("/v1/user-types")
    @Operation(summary = "List all user types")
    public ResponseEntity<ApiResponse<Page<UserTypeResponse>>> listUserTypes(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(accessControlService.listUserTypes(pageable)));
    }

    @GetMapping("/v1/user-types/{id}")
    @Operation(summary = "Get user type with permissions")
    public ResponseEntity<ApiResponse<UserTypeResponse>> getUserType(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(accessControlService.getUserType(id)));
    }

    @PostMapping("/v1/user-types")
    @Operation(summary = "Create a user type")
    public ResponseEntity<ApiResponse<UserTypeResponse>> createUserType(
            @Valid @RequestBody UserTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accessControlService.createUserType(request)));
    }

    @PutMapping("/v1/user-types/{id}")
    @Operation(summary = "Update a user type and its permissions")
    public ResponseEntity<ApiResponse<UserTypeResponse>> updateUserType(
            @PathVariable UUID id, @Valid @RequestBody UserTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accessControlService.updateUserType(id, request)));
    }

    @DeleteMapping("/v1/user-types/{id}")
    @Operation(summary = "Delete a user type")
    public ResponseEntity<ApiResponse<Void>> deleteUserType(@PathVariable UUID id) {
        accessControlService.deleteUserType(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ─── UserGroups ───────────────────────────────────────────────────────────

    @GetMapping("/v1/user-groups")
    @Operation(summary = "List all user groups")
    public ResponseEntity<ApiResponse<Page<UserGroupResponse>>> listUserGroups(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(accessControlService.listUserGroups(pageable)));
    }

    @GetMapping("/v1/user-groups/{id}")
    @Operation(summary = "Get user group")
    public ResponseEntity<ApiResponse<UserGroupResponse>> getUserGroup(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(accessControlService.getUserGroup(id)));
    }

    @PostMapping("/v1/user-groups")
    @Operation(summary = "Create a user group")
    public ResponseEntity<ApiResponse<UserGroupResponse>> createUserGroup(
            @Valid @RequestBody UserGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accessControlService.createUserGroup(request)));
    }

    @PutMapping("/v1/user-groups/{id}")
    @Operation(summary = "Update a user group")
    public ResponseEntity<ApiResponse<UserGroupResponse>> updateUserGroup(
            @PathVariable UUID id, @Valid @RequestBody UserGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accessControlService.updateUserGroup(id, request)));
    }

    @DeleteMapping("/v1/user-groups/{id}")
    @Operation(summary = "Delete a user group")
    public ResponseEntity<ApiResponse<Void>> deleteUserGroup(@PathVariable UUID id) {
        accessControlService.deleteUserGroup(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
