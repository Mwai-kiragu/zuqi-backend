package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.common.DeactivateRequest;
import com.zuqi.api.dto.customer.BlacklistRequest;
import com.zuqi.api.dto.customer.CustomerCategoryRequest;
import com.zuqi.api.dto.customer.CustomerCategoryResponse;
import com.zuqi.api.dto.customer.CustomerRequest;
import com.zuqi.api.dto.customer.CustomerResponse;
import com.zuqi.domain.user.User;
import com.zuqi.service.CustomerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer (retailer/merchant) management APIs")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @Operation(summary = "Get all customers")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getAllCustomers(
            @RequestParam(required = false) UUID distributorId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) UUID salesRepId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "businessName", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<CustomerResponse> customers;
        if (search != null && !search.isBlank()) {
            customers = customerService.searchCustomers(search, distributorId, active, pageable);
        } else if (distributorId != null) {
            if (active == null) {
                customers = customerService.getAllCustomersByDistributor(distributorId, pageable);
            } else {
                customers = active ? customerService.getCustomersByDistributor(distributorId, pageable)
                        : customerService.getInactiveCustomersByDistributor(distributorId, pageable);
            }
        } else if (categoryId != null) {
            customers = customerService.getCustomersByCategory(categoryId, pageable);
        } else if (salesRepId != null) {
            customers = customerService.getCustomersBySalesRep(salesRepId, pageable);
        } else {
            if (active == null) {
                customers = customerService.getAllCustomersIncludingInactive(pageable);
            } else {
                customers = active ? customerService.getAllCustomers(pageable) : customerService.getInactiveCustomers(pageable);
            }
        }
        return ResponseEntity.ok(ApiResponse.success(customers));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getCustomerById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'SALES_REP', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer created successfully", customerService.createCustomer(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'SALES_REP', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", customerService.updateCustomer(id, request)));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> assignSalesRep(@PathVariable UUID id, @RequestParam UUID salesRepId) {
        return ResponseEntity.ok(ApiResponse.success("Sales rep assigned successfully", customerService.assignSalesRep(id, salesRepId)));
    }

    @PatchMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> verifyCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Customer verified successfully", customerService.verifyCustomer(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateCustomer(@PathVariable UUID id,
            @Valid @RequestBody DeactivateRequest request,
            @AuthenticationPrincipal User currentUser) {
        customerService.deactivateCustomer(id, request.getReason(), currentUser);
        return ResponseEntity.ok(ApiResponse.success("Customer deactivated successfully"));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateCustomer(@PathVariable UUID id) {
        customerService.activateCustomer(id);
        return ResponseEntity.ok(ApiResponse.success("Customer activated successfully"));
    }

    @PostMapping("/{id}/blacklist")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> blacklistCustomer(@PathVariable UUID id,
            @Valid @RequestBody BlacklistRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success("Customer blacklisted successfully",
                customerService.blacklistCustomer(id, request.getReason(), currentUser)));
    }

    @PostMapping("/{id}/unblacklist")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> unblacklistCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Customer removed from blacklist",
                customerService.unblacklistCustomer(id)));
    }

    @GetMapping("/blacklisted")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getBlacklistedCustomers(
            @PageableDefault(size = 20, sort = "businessName", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getBlacklistedCustomers(pageable)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CustomerCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(customerService.getAllCategories()));
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<CustomerCategoryResponse>> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getCategoryById(id)));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<CustomerCategoryResponse>> createCategory(@Valid @RequestBody CustomerCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", customerService.createCategory(request)));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<CustomerCategoryResponse>> updateCategory(@PathVariable Long id,
            @Valid @RequestBody CustomerCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", customerService.updateCategory(id, request)));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DISTRIBUTOR_ADMIN', 'MERCHANT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        customerService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully"));
    }

    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<String>>> getCities() {
        return ResponseEntity.ok(ApiResponse.success(customerService.getDistinctCities()));
    }
}
