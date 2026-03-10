package com.zuqi.service.impl;

import com.zuqi.api.dto.customer.CustomerCategoryRequest;
import com.zuqi.api.dto.customer.CustomerCategoryResponse;
import com.zuqi.api.dto.customer.CustomerRequest;
import com.zuqi.api.dto.customer.CustomerResponse;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.customer.CustomerCategory;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.user.User;
import com.zuqi.exception.DuplicateResourceException;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.CustomerCategoryRepository;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.UserRepository;
import com.zuqi.ai.event.MerchantCreatedEvent;
import com.zuqi.ai.feature.FeatureStore;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.CustomerService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerCategoryRepository categoryRepository;
    private final DistributorRepository distributorRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final ActivityLogService activityLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final FeatureStore featureStore;

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAllCustomers(Pageable pageable) {
        log.debug("Fetching all customers");
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return customerRepository.findByDistributorMerchantIdAndActiveTrue(merchantId, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return customerRepository.findByDistributorIdAndActiveTrue(distributorId, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        return customerRepository.findByActiveTrue(pageable).map(CustomerResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomersByDistributor(UUID distributorId, Pageable pageable) {
        return customerRepository.findByDistributorIdAndActiveTrue(distributorId, pageable)
                .map(CustomerResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomersBySalesRep(UUID salesRepId, Pageable pageable) {
        return customerRepository.findByAssignedSalesRepIdAndActiveTrue(salesRepId, pageable)
                .map(CustomerResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getCustomersByCategory(Long categoryId, Pageable pageable) {
        return customerRepository.findByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(CustomerResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> searchCustomers(String searchTerm, UUID distributorId, Boolean active, Pageable pageable) {
        UUID effectiveDistributorId = distributorId;
        if (effectiveDistributorId == null) {
            UUID merchantId = securityUtils.getCurrentUserMerchantId();
            if (merchantId != null) {
                return customerRepository.searchByMerchantAndActive(merchantId, searchTerm, active, pageable)
                        .map(CustomerResponse::fromEntity);
            }
            effectiveDistributorId = securityUtils.getDistributorIdForFiltering();
        }
        if (effectiveDistributorId != null) {
            return customerRepository.searchByDistributorAndActive(effectiveDistributorId, searchTerm, active, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        return customerRepository.searchByBusinessNameAndActive(searchTerm, active, pageable)
                .map(CustomerResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id.toString()));
        return CustomerResponse.fromEntity(customer);
    }

    private String generateCustomerCode() {
        long count = customerRepository.countAll();
        return String.format("CUST-%05d", count + 1);
    }

    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        log.info("Creating new customer: {}", request.getBusinessName());

        if (customerRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Customer", "phone", request.getPhone());
        }
        if (request.getEmail() != null && customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Customer", "email", request.getEmail());
        }
        if (request.getKraPin() != null && customerRepository.existsByKraPin(request.getKraPin())) {
            throw new DuplicateResourceException("Customer", "kraPin", request.getKraPin());
        }

        Customer customer = Customer.builder()
                .customerCode(generateCustomerCode())
                .businessName(request.getBusinessName())
                .ownerName(request.getOwnerName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .county(request.getCounty())
                .subCounty(request.getSubCounty())
                .kraPin(request.getKraPin())
                .contactPersons(request.getContactPersons() != null ? request.getContactPersons() : new java.util.ArrayList<>())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .creditLimit(request.getCreditLimit())
                .paymentTermsDays(request.getPaymentTermsDays() != null ? request.getPaymentTermsDays() : 0)
                .build();

        if (request.getCategoryId() != null) {
            CustomerCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("CustomerCategory", "id", request.getCategoryId().toString()));
            customer.setCategory(category);
        }
        if (request.getDistributorId() != null) {
            Distributor distributor = distributorRepository.findById(request.getDistributorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId().toString()));
            customer.setDistributor(distributor);
        }
        if (request.getAssignedSalesRepId() != null) {
            User salesRep = userRepository.findById(request.getAssignedSalesRepId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getAssignedSalesRepId().toString()));
            customer.setAssignedSalesRep(salesRep);
        }

        Customer saved = customerRepository.save(customer);
        log.info("Customer created with ID: {}", saved.getId());

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(currentUser.getId(), currentUser.getEmail(),
                    currentUser.getFirstName() + " " + currentUser.getLastName(),
                    ActivityAction.CREATE, "CUSTOMER", saved.getId(),
                    saved.getBusinessName(), "MERCHANTS", "Created customer: " + saved.getBusinessName());
        }

        publishCustomerCreatedEvent(saved);

        return CustomerResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(UUID id, CustomerRequest request) {
        log.info("Updating customer: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id.toString()));

        if (!customer.getPhone().equals(request.getPhone()) && customerRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Customer", "phone", request.getPhone());
        }
        if (request.getEmail() != null && !request.getEmail().equals(customer.getEmail())
                && customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Customer", "email", request.getEmail());
        }
        if (request.getKraPin() != null && !request.getKraPin().equals(customer.getKraPin())
                && customerRepository.existsByKraPin(request.getKraPin())) {
            throw new DuplicateResourceException("Customer", "kraPin", request.getKraPin());
        }

        customer.setBusinessName(request.getBusinessName());
        customer.setOwnerName(request.getOwnerName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setAddress(request.getAddress());
        customer.setCity(request.getCity());
        customer.setCounty(request.getCounty());
        customer.setSubCounty(request.getSubCounty());
        customer.setKraPin(request.getKraPin());
        if (request.getContactPersons() != null) customer.setContactPersons(request.getContactPersons());
        customer.setLatitude(request.getLatitude());
        customer.setLongitude(request.getLongitude());
        if (request.getCreditLimit() != null) customer.setCreditLimit(request.getCreditLimit());
        if (request.getPaymentTermsDays() != null) customer.setPaymentTermsDays(request.getPaymentTermsDays());

        if (request.getCategoryId() != null) {
            CustomerCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("CustomerCategory", "id", request.getCategoryId().toString()));
            customer.setCategory(category);
        }
        if (request.getDistributorId() != null) {
            Distributor distributor = distributorRepository.findById(request.getDistributorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Distributor", "id", request.getDistributorId().toString()));
            customer.setDistributor(distributor);
        }
        if (request.getAssignedSalesRepId() != null) {
            User salesRep = userRepository.findById(request.getAssignedSalesRepId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getAssignedSalesRepId().toString()));
            customer.setAssignedSalesRep(salesRep);
        }

        Customer updated = customerRepository.save(customer);
        featureStore.invalidateMerchantCache(id);
        return CustomerResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public CustomerResponse assignSalesRep(UUID customerId, UUID salesRepId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId.toString()));
        User salesRep = userRepository.findById(salesRepId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", salesRepId.toString()));
        customer.setAssignedSalesRep(salesRep);
        return CustomerResponse.fromEntity(customerRepository.save(customer));
    }

    @Override
    @Transactional
    public CustomerResponse verifyCustomer(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId.toString()));
        customer.setVerified(true);
        return CustomerResponse.fromEntity(customerRepository.save(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getInactiveCustomers(Pageable pageable) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return customerRepository.findByDistributorMerchantIdAndActiveFalse(merchantId, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return customerRepository.findByDistributorIdAndActiveFalse(distributorId, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        return customerRepository.findByActiveFalse(pageable).map(CustomerResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getInactiveCustomersByDistributor(UUID distributorId, Pageable pageable) {
        return customerRepository.findByDistributorIdAndActiveFalse(distributorId, pageable)
                .map(CustomerResponse::fromEntity);
    }

    @Override
    @Transactional
    public void deactivateCustomer(UUID id, String reason, User currentUser) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id.toString()));
        customer.setActive(false);
        customer.setDeactivationReason(reason);
        customer.setDeactivatedAt(LocalDateTime.now());
        customer.setDeactivatedBy(currentUser);
        customerRepository.save(customer);
    }

    @Override
    @Transactional
    public void activateCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id.toString()));
        customer.setActive(true);
        customer.setDeactivationReason(null);
        customer.setDeactivatedAt(null);
        customer.setDeactivatedBy(null);
        customerRepository.save(customer);
    }

    @Override
    @Transactional
    public CustomerResponse blacklistCustomer(UUID id, String reason, User currentUser) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id.toString()));
        customer.setBlacklisted(true);
        customer.setBlacklistedReason(reason);
        customer.setBlacklistedAt(LocalDateTime.now());
        customer.setBlacklistedBy(currentUser);
        customer.setActive(false);
        return CustomerResponse.fromEntity(customerRepository.save(customer));
    }

    @Override
    @Transactional
    public CustomerResponse unblacklistCustomer(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id.toString()));
        customer.setBlacklisted(false);
        customer.setBlacklistedReason(null);
        customer.setBlacklistedAt(null);
        customer.setBlacklistedBy(null);
        customer.setActive(true);
        return CustomerResponse.fromEntity(customerRepository.save(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getBlacklistedCustomers(Pageable pageable) {
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            return customerRepository.findByDistributorMerchantIdAndBlacklistedTrue(merchantId, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            return customerRepository.findByDistributorIdAndBlacklistedTrue(distributorId, pageable)
                    .map(CustomerResponse::fromEntity);
        }
        return customerRepository.findByBlacklistedTrue(pageable).map(CustomerResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerCategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream().map(CustomerCategoryResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerCategoryResponse getCategoryById(Long id) {
        CustomerCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerCategory", "id", id.toString()));
        return CustomerCategoryResponse.fromEntity(category);
    }

    @Override
    @Transactional
    public CustomerCategoryResponse createCategory(CustomerCategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("CustomerCategory", "name", request.getName());
        }
        CustomerCategory category = CustomerCategory.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return CustomerCategoryResponse.fromEntity(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CustomerCategoryResponse updateCategory(Long id, CustomerCategoryRequest request) {
        CustomerCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerCategory", "id", id.toString()));
        if (!category.getName().equals(request.getName()) && categoryRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("CustomerCategory", "name", request.getName());
        }
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        return CustomerCategoryResponse.fromEntity(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        CustomerCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerCategory", "id", id.toString()));
        if (customerRepository.existsByCategoryId(id)) {
            throw new IllegalStateException("Cannot delete category that is assigned to customers");
        }
        categoryRepository.delete(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getDistinctCities() {
        return customerRepository.findDistinctCities();
    }

    private void publishCustomerCreatedEvent(Customer customer) {
        if (customer.getDistributor() == null) {
            log.debug("Skipping MerchantCreatedEvent for customer {} - no distributor assigned", customer.getId());
            return;
        }
        MerchantCreatedEvent event = new MerchantCreatedEvent(
                customer.getId(),
                customer.getDistributor().getId(),
                customer.getBusinessName(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getCategory() != null ? customer.getCategory().getId() : null,
                customer.getAssignedSalesRep() != null ? customer.getAssignedSalesRep().getId() : null,
                customer.getCreatedAt()
        );
        eventPublisher.publishEvent(event);
    }
}
