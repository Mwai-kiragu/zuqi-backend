package com.zuqi.service;

import com.zuqi.api.dto.billing.AssignSubscriptionRequest;
import com.zuqi.api.dto.billing.BillingModuleRequest;
import com.zuqi.api.dto.billing.BillingModuleResponse;
import com.zuqi.api.dto.billing.BillingPackageRequest;
import com.zuqi.api.dto.billing.BillingPackageResponse;
import com.zuqi.api.dto.billing.PackageDefinitionResponse;
import com.zuqi.api.dto.billing.SubscriptionResponse;
import com.zuqi.domain.user.User;

import java.util.List;
import java.util.UUID;

public interface BillingService {

    // ── Subscriptions ─────────────────────────────────────────────────────────

    List<SubscriptionResponse> getAll();

    SubscriptionResponse getByDistributorId(UUID distributorId);

    SubscriptionResponse assign(AssignSubscriptionRequest request, User currentUser);

    SubscriptionResponse update(UUID subscriptionId, AssignSubscriptionRequest request, User currentUser);

    void deactivate(UUID subscriptionId, User currentUser);

    /** Legacy — delegates to getAllPackageDefinitions() */
    List<PackageDefinitionResponse> getPackageDefinitions();

    // ── Modules ───────────────────────────────────────────────────────────────

    List<BillingModuleResponse> getAllModules();

    BillingModuleResponse createModule(BillingModuleRequest request);

    BillingModuleResponse updateModule(UUID id, BillingModuleRequest request);

    void deactivateModule(UUID id);

    // ── Package Definitions ───────────────────────────────────────────────────

    List<BillingPackageResponse> getAllPackageDefinitions();

    BillingPackageResponse createPackageDefinition(BillingPackageRequest request);

    BillingPackageResponse updatePackageDefinition(UUID id, BillingPackageRequest request);

    void deactivatePackageDefinition(UUID id);
}
