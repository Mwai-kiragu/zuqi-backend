package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.role.RoleRequest;
import com.zuqi.api.dto.role.RoleResponse;
import com.zuqi.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Role management APIs")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "Get all roles", description = "Retrieve all roles (system and custom)")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = roleService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved successfully", roles));
    }

    @GetMapping("/system")
    @Operation(summary = "Get system roles", description = "Retrieve only system-defined roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getSystemRoles() {
        List<RoleResponse> roles = roleService.getSystemRoles();
        return ResponseEntity.ok(ApiResponse.success("System roles retrieved successfully", roles));
    }

    @GetMapping("/custom")
    @Operation(summary = "Get custom roles", description = "Retrieve only custom-created roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getCustomRoles() {
        List<RoleResponse> roles = roleService.getCustomRoles();
        return ResponseEntity.ok(ApiResponse.success("Custom roles retrieved successfully", roles));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get role by ID", description = "Retrieve a specific role by its ID")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Long id) {
        RoleResponse role = roleService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.success("Role retrieved successfully", role));
    }

    @GetMapping("/name/{name}")
    @Operation(summary = "Get role by name", description = "Retrieve a specific role by its name")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleByName(@PathVariable String name) {
        RoleResponse role = roleService.getRoleByName(name);
        return ResponseEntity.ok(ApiResponse.success("Role retrieved successfully", role));
    }

    @PostMapping
    @Operation(summary = "Create role", description = "Create a new custom role")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody RoleRequest request) {
        RoleResponse role = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created successfully", role));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update role", description = "Update an existing role")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleRequest request) {
        RoleResponse role = roleService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", role));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete role", description = "Delete a custom role (system roles cannot be deleted)")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted successfully"));
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "Update role permissions", description = "Update the permissions assigned to a role")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRolePermissions(
            @PathVariable Long id,
            @RequestBody List<Long> permissionIds) {
        RoleResponse role = roleService.updateRolePermissions(id, permissionIds);
        return ResponseEntity.ok(ApiResponse.success("Role permissions updated successfully", role));
    }
}
