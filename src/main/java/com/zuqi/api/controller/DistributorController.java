package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.distributor.DistributorRequest;
import com.zuqi.api.dto.distributor.DistributorResponse;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.user.RoleName;
import com.zuqi.domain.user.User;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.MerchantRepository;
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
@RequestMapping("/v1/distributors")
@RequiredArgsConstructor
@Tag(name = "Distributors", description = "Distributor management APIs")
public class DistributorController {

    private final DistributorRepository distributorRepository;
    private final MerchantRepository merchantRepository;

    @GetMapping
    @Operation(summary = "Get all active distributors (MERCHANT_ADMIN sees only their own)")
    public ResponseEntity<ApiResponse<List<DistributorResponse>>> getAllDistributors(
            @AuthenticationPrincipal User currentUser) {
        List<Distributor> distributors;

        boolean isMerchantAdmin = currentUser != null && currentUser.getRoles().stream()
                .anyMatch(r -> r.isRole(RoleName.MERCHANT_ADMIN));

        if (isMerchantAdmin && currentUser.getMerchantId() != null) {
            distributors = distributorRepository.findByMerchantIdAndActiveTrue(currentUser.getMerchantId());
        } else {
            distributors = distributorRepository.findByActiveTrue();
        }

        List<DistributorResponse> response = distributors.stream()
                .map(DistributorResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Distributors retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get distributor by ID")
    public ResponseEntity<ApiResponse<DistributorResponse>> getDistributorById(@PathVariable UUID id) {
        Distributor distributor = distributorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Distributor not found"));

        return ResponseEntity.ok(ApiResponse.success("Distributor retrieved successfully",
                DistributorResponse.fromEntity(distributor)));
    }

    @PostMapping
    @Operation(summary = "Create a new distributor (MERCHANT_ADMIN auto-links to their merchant)")
    public ResponseEntity<ApiResponse<DistributorResponse>> createDistributor(
            @Valid @RequestBody DistributorRequest request,
            @AuthenticationPrincipal User currentUser) {

        if (distributorRepository.existsByName(request.getName())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Distributor with this name already exists"));
        }

        // Resolve merchant: MERCHANT_ADMIN links to their own merchant
        Merchant merchant = null;
        boolean isMerchantAdmin = currentUser != null && currentUser.getRoles().stream()
                .anyMatch(r -> r.isRole(RoleName.MERCHANT_ADMIN));
        if (isMerchantAdmin && currentUser.getMerchantId() != null) {
            merchant = merchantRepository.findById(currentUser.getMerchantId()).orElse(null);
        }

        Distributor distributor = Distributor.builder()
                .name(request.getName())
                .registrationNumber(request.getRegistrationNumber())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry() != null ? request.getCountry() : "Kenya")
                .merchant(merchant)
                .active(true)
                .build();

        Distributor saved = distributorRepository.save(distributor);

        return ResponseEntity.ok(ApiResponse.success("Distributor created successfully",
                DistributorResponse.fromEntity(saved)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a distributor")
    public ResponseEntity<ApiResponse<DistributorResponse>> updateDistributor(
            @PathVariable UUID id,
            @Valid @RequestBody DistributorRequest request) {

        Distributor distributor = distributorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Distributor not found"));

        // Check for duplicate name (excluding current distributor)
        distributorRepository.findByName(request.getName())
                .filter(d -> !d.getId().equals(id))
                .ifPresent(d -> {
                    throw new RuntimeException("Distributor with this name already exists");
                });

        distributor.setName(request.getName());
        distributor.setRegistrationNumber(request.getRegistrationNumber());
        distributor.setEmail(request.getEmail());
        distributor.setPhone(request.getPhone());
        distributor.setAddress(request.getAddress());
        distributor.setCity(request.getCity());
        if (request.getCountry() != null) {
            distributor.setCountry(request.getCountry());
        }

        Distributor saved = distributorRepository.save(distributor);

        return ResponseEntity.ok(ApiResponse.success("Distributor updated successfully",
                DistributorResponse.fromEntity(saved)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a distributor")
    public ResponseEntity<ApiResponse<Void>> deactivateDistributor(@PathVariable UUID id) {
        Distributor distributor = distributorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Distributor not found"));

        distributor.setActive(false);
        distributorRepository.save(distributor);

        return ResponseEntity.ok(ApiResponse.success("Distributor deactivated successfully"));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a distributor")
    public ResponseEntity<ApiResponse<Void>> activateDistributor(@PathVariable UUID id) {
        Distributor distributor = distributorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Distributor not found"));

        distributor.setActive(true);
        distributorRepository.save(distributor);

        return ResponseEntity.ok(ApiResponse.success("Distributor activated successfully"));
    }
}
