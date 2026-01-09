package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.distributor.DistributorRequest;
import com.zuqi.api.dto.distributor.DistributorResponse;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.DistributorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/distributors")
@RequiredArgsConstructor
@Tag(name = "Distributors", description = "Distributor management APIs")
public class DistributorController {

    private final DistributorRepository distributorRepository;

    @GetMapping
    @Operation(summary = "Get all active distributors")
    public ResponseEntity<ApiResponse<List<DistributorResponse>>> getAllDistributors() {
        List<DistributorResponse> distributors = distributorRepository.findByActiveTrue()
                .stream()
                .map(DistributorResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Distributors retrieved successfully", distributors));
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
    @Operation(summary = "Create a new distributor")
    public ResponseEntity<ApiResponse<DistributorResponse>> createDistributor(
            @Valid @RequestBody DistributorRequest request) {

        if (distributorRepository.existsByName(request.getName())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Distributor with this name already exists"));
        }

        Distributor distributor = Distributor.builder()
                .name(request.getName())
                .registrationNumber(request.getRegistrationNumber())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry() != null ? request.getCountry() : "Kenya")
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
