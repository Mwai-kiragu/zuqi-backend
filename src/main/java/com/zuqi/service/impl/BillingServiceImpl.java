package com.zuqi.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.api.dto.billing.AssignSubscriptionRequest;
import com.zuqi.api.dto.billing.BillingModuleRequest;
import com.zuqi.api.dto.billing.BillingModuleResponse;
import com.zuqi.api.dto.billing.BillingPackageRequest;
import com.zuqi.api.dto.billing.BillingPackageResponse;
import com.zuqi.api.dto.billing.PackageDefinitionResponse;
import com.zuqi.api.dto.billing.SubscriptionResponse;
import com.zuqi.domain.billing.BillingModule;
import com.zuqi.domain.billing.BillingPackageDefinition;
import com.zuqi.domain.billing.BillingPackageType;
import com.zuqi.domain.billing.DistributorSubscription;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.user.User;
import com.zuqi.repository.BillingModuleRepository;
import com.zuqi.repository.BillingPackageDefinitionRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.DistributorSubscriptionRepository;
import com.zuqi.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final DistributorSubscriptionRepository subscriptionRepository;
    private final DistributorRepository distributorRepository;
    private final BillingModuleRepository moduleRepository;
    private final BillingPackageDefinitionRepository packageRepository;
    private final ObjectMapper objectMapper;

    // ── Subscriptions ─────────────────────────────────────────────────────────

    @Override
    public List<SubscriptionResponse> getAll() {
        return subscriptionRepository.findAllByActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(SubscriptionResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public SubscriptionResponse getByDistributorId(UUID distributorId) {
        return subscriptionRepository.findByDistributorId(distributorId)
                .map(SubscriptionResponse::fromEntity)
                .orElseGet(() -> {
                    Distributor distributor = distributorRepository.findById(distributorId)
                            .orElseThrow(() -> new RuntimeException("Distributor not found: " + distributorId));
                    return SubscriptionResponse.defaultFreeTrial(distributorId, distributor.getName());
                });
    }

    @Override
    @Transactional
    public SubscriptionResponse assign(AssignSubscriptionRequest request, User currentUser) {
        Distributor distributor = distributorRepository.findById(request.getDistributorId())
                .orElseThrow(() -> new RuntimeException("Distributor not found"));

        DistributorSubscription sub = subscriptionRepository
                .findByDistributorId(request.getDistributorId())
                .orElse(DistributorSubscription.builder()
                        .distributor(distributor)
                        .build());

        applyRequest(sub, request, currentUser);
        sub.setCreatedBy(sub.getId() == null ? currentUser : sub.getCreatedBy());

        DistributorSubscription saved = subscriptionRepository.save(sub);
        return SubscriptionResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public SubscriptionResponse update(UUID distributorId, AssignSubscriptionRequest request, User currentUser) {
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new RuntimeException("Distributor not found: " + distributorId));

        DistributorSubscription sub = subscriptionRepository.findByDistributorId(distributorId)
                .orElse(DistributorSubscription.builder()
                        .distributor(distributor)
                        .build());

        applyRequest(sub, request, currentUser);
        sub.setCreatedBy(sub.getId() == null ? currentUser : sub.getCreatedBy());
        DistributorSubscription saved = subscriptionRepository.save(sub);
        return SubscriptionResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deactivate(UUID subscriptionId, User currentUser) {
        DistributorSubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));
        sub.setActive(false);
        sub.setUpdatedBy(currentUser);
        subscriptionRepository.save(sub);
    }

    /** Legacy endpoint — map DB packages to the old PackageDefinitionResponse shape */
    @Override
    public List<PackageDefinitionResponse> getPackageDefinitions() {
        List<BillingPackageResponse> dbPkgs = getAllPackageDefinitions();
        if (dbPkgs.isEmpty()) {
            return PackageDefinitionResponse.all();
        }
        return dbPkgs.stream()
                .map(p -> PackageDefinitionResponse.builder()
                        .name(p.getName())
                        .displayName(p.getDisplayName())
                        .includedModules(p.getModules())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Modules ───────────────────────────────────────────────────────────────

    @Override
    public List<BillingModuleResponse> getAllModules() {
        return moduleRepository.findAllByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(BillingModuleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BillingModuleResponse createModule(BillingModuleRequest request) {
        BillingModule module = BillingModule.builder()
                .moduleKey(request.getModuleKey())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        return BillingModuleResponse.fromEntity(moduleRepository.save(module));
    }

    @Override
    @Transactional
    public BillingModuleResponse updateModule(UUID id, BillingModuleRequest request) {
        BillingModule module = moduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Module not found: " + id));
        module.setDisplayName(request.getDisplayName());
        module.setDescription(request.getDescription());
        if (request.getSortOrder() != null) {
            module.setSortOrder(request.getSortOrder());
        }
        return BillingModuleResponse.fromEntity(moduleRepository.save(module));
    }

    @Override
    @Transactional
    public void deactivateModule(UUID id) {
        BillingModule module = moduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Module not found: " + id));
        module.setActive(false);
        moduleRepository.save(module);
    }

    // ── Package Definitions ───────────────────────────────────────────────────

    @Override
    public List<BillingPackageResponse> getAllPackageDefinitions() {
        return packageRepository.findAllByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(BillingPackageResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BillingPackageResponse createPackageDefinition(BillingPackageRequest request) {
        String modulesJson = toJson(request.getModules());
        BillingPackageDefinition pkg = BillingPackageDefinition.builder()
                .name(request.getName())
                .displayName(request.getDisplayName())
                .description(request.getDescription())
                .isSystem(false)
                .modules(modulesJson)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        return BillingPackageResponse.fromEntity(packageRepository.save(pkg));
    }

    @Override
    @Transactional
    public BillingPackageResponse updatePackageDefinition(UUID id, BillingPackageRequest request) {
        BillingPackageDefinition pkg = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found: " + id));
        pkg.setDisplayName(request.getDisplayName());
        pkg.setDescription(request.getDescription());
        pkg.setModules(toJson(request.getModules()));
        if (request.getSortOrder() != null) {
            pkg.setSortOrder(request.getSortOrder());
        }
        return BillingPackageResponse.fromEntity(packageRepository.save(pkg));
    }

    @Override
    @Transactional
    public void deactivatePackageDefinition(UUID id) {
        BillingPackageDefinition pkg = packageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found: " + id));
        if (pkg.isSystem()) {
            throw new IllegalStateException("System packages cannot be deactivated");
        }
        pkg.setActive(false);
        packageRepository.save(pkg);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyRequest(DistributorSubscription sub, AssignSubscriptionRequest req, User actor) {
        sub.setPackageType(req.getPackageType());
        sub.setActive(true);

        // Use explicit startDate if provided, otherwise default to today
        LocalDate start = req.getStartDate() != null ? req.getStartDate() : LocalDate.now();
        sub.setStartDate(start);

        // Use explicit endDate > durationDays > null (unlimited)
        if (req.getEndDate() != null) {
            sub.setEndDate(req.getEndDate());
        } else if (req.getDurationDays() != null) {
            sub.setEndDate(start.plusDays(req.getDurationDays()));
        } else {
            sub.setEndDate(null);
        }

        sub.setNotes(req.getNotes());
        sub.setUpdatedBy(actor);

        if (req.getCustomModules() != null && !req.getCustomModules().isEmpty()) {
            sub.setCustomModules(toJson(req.getCustomModules()));
        } else {
            sub.setCustomModules(null);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
