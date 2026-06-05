package com.zuqi.service;

import com.zuqi.api.dto.customer.CreditStatsResponse;
import com.zuqi.api.dto.customer.CustomerCategoryRequest;
import com.zuqi.api.dto.customer.CustomerCategoryResponse;
import com.zuqi.api.dto.customer.CustomerRequest;
import com.zuqi.api.dto.customer.CustomerResponse;
import com.zuqi.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CustomerService {

    Page<CustomerResponse> getAllCustomers(Pageable pageable);

    Page<CustomerResponse> getAllCustomersIncludingInactive(Pageable pageable);

    Page<CustomerResponse> getInactiveCustomers(Pageable pageable);

    Page<CustomerResponse> getCustomersByDistributor(UUID distributorId, Pageable pageable);

    Page<CustomerResponse> getAllCustomersByDistributor(UUID distributorId, Pageable pageable);

    Page<CustomerResponse> getInactiveCustomersByDistributor(UUID distributorId, Pageable pageable);

    Page<CustomerResponse> getCustomersBySalesRep(UUID salesRepId, Pageable pageable);

    Page<CustomerResponse> getCustomersByCategory(Long categoryId, Pageable pageable);

    Page<CustomerResponse> searchCustomers(String searchTerm, UUID distributorId, Boolean active, Pageable pageable);

    CustomerResponse getCustomerById(UUID id);

    CustomerResponse createCustomer(CustomerRequest request);

    CustomerResponse updateCustomer(UUID id, CustomerRequest request);

    CustomerResponse assignSalesRep(UUID customerId, UUID salesRepId);

    CustomerResponse verifyCustomer(UUID customerId);

    void deactivateCustomer(UUID id, String reason, User currentUser);

    void activateCustomer(UUID id);

    CustomerResponse blacklistCustomer(UUID id, String reason, User currentUser);

    CustomerResponse unblacklistCustomer(UUID id);

    Page<CustomerResponse> getBlacklistedCustomers(Pageable pageable);

    List<CustomerCategoryResponse> getAllCategories();

    CustomerCategoryResponse getCategoryById(Long id);

    CustomerCategoryResponse createCategory(CustomerCategoryRequest request);

    CustomerCategoryResponse updateCategory(Long id, CustomerCategoryRequest request);

    void deleteCategory(Long id);

    List<String> getDistinctCities();

    CreditStatsResponse getCreditStats();
}
