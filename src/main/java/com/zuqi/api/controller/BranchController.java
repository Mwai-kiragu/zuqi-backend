package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.branch.*;
import com.zuqi.service.BranchService;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/branches")
@RequiredArgsConstructor
@Tag(name = "Branch Management", description = "Branch management endpoints for distributors")
public class BranchController {

    private final BranchService branchService;
    private final SecurityUtils securityUtils;

    @PostMapping
    @Operation(summary = "Create a new branch")
    public ResponseEntity<ApiResponse<BranchResponse>> createBranch(@Valid @RequestBody BranchRequest request) {
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        UUID userId = securityUtils.getCurrentUserId();
        BranchResponse response = branchService.createBranch(request, distributorId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Branch created", response));
    }

    @GetMapping
    @Operation(summary = "List all branches for the current distributor")
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getBranches() {
        UUID distributorId = securityUtils.getCurrentUserDistributorId();
        return ResponseEntity.ok(ApiResponse.success(branchService.getBranchesByDistributor(distributorId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get branch by ID")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(branchService.getBranchById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a branch")
    public ResponseEntity<ApiResponse<BranchResponse>> updateBranch(
            @PathVariable UUID id, @Valid @RequestBody BranchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Branch updated", branchService.updateBranch(id, request)));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a branch")
    public ResponseEntity<ApiResponse<Void>> activateBranch(@PathVariable UUID id) {
        branchService.activateBranch(id);
        return ResponseEntity.ok(ApiResponse.success("Branch activated"));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a branch")
    public ResponseEntity<ApiResponse<Void>> deactivateBranch(@PathVariable UUID id) {
        branchService.deactivateBranch(id);
        return ResponseEntity.ok(ApiResponse.success("Branch deactivated"));
    }

    @PostMapping("/{id}/users")
    @Operation(summary = "Add a user to a branch")
    public ResponseEntity<ApiResponse<BranchUserResponse>> addUser(
            @PathVariable UUID id, @Valid @RequestBody BranchUserRequest request) {
        UUID assignedBy = securityUtils.getCurrentUserId();
        BranchUserResponse response = branchService.addUserToBranch(id, request, assignedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("User added to branch", response));
    }

    @GetMapping("/{id}/users")
    @Operation(summary = "List users in a branch")
    public ResponseEntity<ApiResponse<List<BranchUserResponse>>> getUsers(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(branchService.getUsersByBranch(id)));
    }

    @DeleteMapping("/{id}/users/{userId}")
    @Operation(summary = "Remove a user from a branch")
    public ResponseEntity<ApiResponse<Void>> removeUser(@PathVariable UUID id, @PathVariable UUID userId) {
        branchService.removeUserFromBranch(id, userId);
        return ResponseEntity.ok(ApiResponse.success("User removed from branch"));
    }
}
