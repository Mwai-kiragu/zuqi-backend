package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.role.PermissionFullResponse;
import com.zuqi.api.dto.role.PermissionRequest;
import com.zuqi.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permission management APIs")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @Operation(summary = "Get all permissions", description = "Retrieve all permissions")
    public ResponseEntity<ApiResponse<List<PermissionFullResponse>>> getAllPermissions() {
        List<PermissionFullResponse> permissions = permissionService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved successfully", permissions));
    }

    @GetMapping("/modules")
    @Operation(summary = "Get all modules", description = "Retrieve all distinct permission modules")
    public ResponseEntity<ApiResponse<List<String>>> getAllModules() {
        List<String> modules = permissionService.getAllModules();
        return ResponseEntity.ok(ApiResponse.success("Modules retrieved successfully", modules));
    }

    @GetMapping("/module/{module}")
    @Operation(summary = "Get permissions by module", description = "Retrieve permissions for a specific module")
    public ResponseEntity<ApiResponse<List<PermissionFullResponse>>> getPermissionsByModule(
            @PathVariable String module) {
        List<PermissionFullResponse> permissions = permissionService.getPermissionsByModule(module);
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved successfully", permissions));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get permission by ID", description = "Retrieve a specific permission by its ID")
    public ResponseEntity<ApiResponse<PermissionFullResponse>> getPermissionById(@PathVariable Long id) {
        PermissionFullResponse permission = permissionService.getPermissionById(id);
        return ResponseEntity.ok(ApiResponse.success("Permission retrieved successfully", permission));
    }

    @GetMapping("/name/{name}")
    @Operation(summary = "Get permission by name", description = "Retrieve a specific permission by its name")
    public ResponseEntity<ApiResponse<PermissionFullResponse>> getPermissionByName(@PathVariable String name) {
        PermissionFullResponse permission = permissionService.getPermissionByName(name);
        return ResponseEntity.ok(ApiResponse.success("Permission retrieved successfully", permission));
    }

    @PostMapping
    @Operation(summary = "Create permission", description = "Create a new permission")
    public ResponseEntity<ApiResponse<PermissionFullResponse>> createPermission(
            @Valid @RequestBody PermissionRequest request) {
        PermissionFullResponse permission = permissionService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Permission created successfully", permission));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update permission", description = "Update an existing permission")
    public ResponseEntity<ApiResponse<PermissionFullResponse>> updatePermission(
            @PathVariable Long id,
            @Valid @RequestBody PermissionRequest request) {
        PermissionFullResponse permission = permissionService.updatePermission(id, request);
        return ResponseEntity.ok(ApiResponse.success("Permission updated successfully", permission));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete permission", description = "Delete a permission (cannot delete if assigned to roles)")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ResponseEntity.ok(ApiResponse.success("Permission deleted successfully"));
    }
}
