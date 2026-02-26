package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.billing.AssignSubscriptionRequest;
import com.zuqi.api.dto.billing.BillingModuleRequest;
import com.zuqi.api.dto.billing.BillingModuleResponse;
import com.zuqi.api.dto.billing.BillingPackageRequest;
import com.zuqi.api.dto.billing.BillingPackageResponse;
import com.zuqi.api.dto.billing.PackageDefinitionResponse;
import com.zuqi.api.dto.billing.SubscriptionResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Subscription package management APIs")
public class BillingController {

    private final BillingService billingService;

    // ── Subscriptions ─────────────────────────────────────────────────────────

    @GetMapping("/subscriptions")
    @Operation(summary = "Get all active subscriptions (SUPER_ADMIN / ADMIN)")
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> getAllSubscriptions() {
        return ResponseEntity.ok(
                ApiResponse.success("Subscriptions retrieved", billingService.getAll()));
    }

    @GetMapping("/subscriptions/{distributorId}")
    @Operation(summary = "Get subscription for a distributor (defaults to FREE_TRIAL if none assigned)")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getByDistributor(
            @PathVariable UUID distributorId) {
        return ResponseEntity.ok(
                ApiResponse.success("Subscription retrieved", billingService.getByDistributorId(distributorId)));
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Assign / upsert a subscription package (SUPER_ADMIN)")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> assign(
            @Valid @RequestBody AssignSubscriptionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                ApiResponse.success("Subscription assigned", billingService.assign(request, currentUser)));
    }

    @PutMapping("/subscriptions/{id}")
    @Operation(summary = "Update an existing subscription (SUPER_ADMIN)")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody AssignSubscriptionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(
                ApiResponse.success("Subscription updated", billingService.update(id, request, currentUser)));
    }

    @DeleteMapping("/subscriptions/{id}")
    @Operation(summary = "Deactivate a subscription (SUPER_ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        billingService.deactivate(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Subscription deactivated"));
    }

    // ── Legacy packages endpoint ───────────────────────────────────────────────

    @GetMapping("/packages")
    @Operation(summary = "List all package definitions with their included modules (legacy)")
    public ResponseEntity<ApiResponse<List<PackageDefinitionResponse>>> getPackages() {
        return ResponseEntity.ok(
                ApiResponse.success("Package definitions retrieved", billingService.getPackageDefinitions()));
    }

    // ── Modules ───────────────────────────────────────────────────────────────

    @GetMapping("/modules")
    @Operation(summary = "Get all active billing modules")
    public ResponseEntity<ApiResponse<List<BillingModuleResponse>>> getAllModules() {
        return ResponseEntity.ok(
                ApiResponse.success("Modules retrieved", billingService.getAllModules()));
    }

    @PostMapping("/modules")
    @Operation(summary = "Create a new billing module (SUPER_ADMIN)")
    public ResponseEntity<ApiResponse<BillingModuleResponse>> createModule(
            @Valid @RequestBody BillingModuleRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Module created", billingService.createModule(request)));
    }

    @PutMapping("/modules/{id}")
    @Operation(summary = "Update a billing module (SUPER_ADMIN)")
    public ResponseEntity<ApiResponse<BillingModuleResponse>> updateModule(
            @PathVariable UUID id,
            @Valid @RequestBody BillingModuleRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Module updated", billingService.updateModule(id, request)));
    }

    @DeleteMapping("/modules/{id}")
    @Operation(summary = "Deactivate a billing module (SUPER_ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deactivateModule(@PathVariable UUID id) {
        billingService.deactivateModule(id);
        return ResponseEntity.ok(ApiResponse.success("Module deactivated"));
    }

    // ── Package Definitions ───────────────────────────────────────────────────

    @GetMapping("/package-definitions")
    @Operation(summary = "Get all active package definitions from DB")
    public ResponseEntity<ApiResponse<List<BillingPackageResponse>>> getAllPackageDefinitions() {
        return ResponseEntity.ok(
                ApiResponse.success("Package definitions retrieved", billingService.getAllPackageDefinitions()));
    }

    @PostMapping("/package-definitions")
    @Operation(summary = "Create a new package definition (SUPER_ADMIN)")
    public ResponseEntity<ApiResponse<BillingPackageResponse>> createPackageDefinition(
            @Valid @RequestBody BillingPackageRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Package created", billingService.createPackageDefinition(request)));
    }

    @PutMapping("/package-definitions/{id}")
    @Operation(summary = "Update a package definition (SUPER_ADMIN)")
    public ResponseEntity<ApiResponse<BillingPackageResponse>> updatePackageDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody BillingPackageRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Package updated", billingService.updatePackageDefinition(id, request)));
    }

    @DeleteMapping("/package-definitions/{id}")
    @Operation(summary = "Deactivate a package definition (SUPER_ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deactivatePackageDefinition(@PathVariable UUID id) {
        billingService.deactivatePackageDefinition(id);
        return ResponseEntity.ok(ApiResponse.success("Package deactivated"));
    }
}
