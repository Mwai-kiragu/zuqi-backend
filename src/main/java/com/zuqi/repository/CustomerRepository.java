package com.zuqi.repository;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.customer.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Page<Customer> findByActiveTrue(Pageable pageable);

    Page<Customer> findByDistributorIdAndActiveTrue(UUID distributorId, Pageable pageable);

    Page<Customer> findByActiveFalse(Pageable pageable);

    Page<Customer> findByDistributorIdAndActiveFalse(UUID distributorId, Pageable pageable);

    Page<Customer> findByAssignedSalesRepIdAndActiveTrue(UUID salesRepId, Pageable pageable);

    Page<Customer> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByKraPin(String kraPin);

    boolean existsByCategoryId(Long categoryId);

    Optional<Customer> findByCustomerCode(String customerCode);

    Optional<Customer> findByKraPin(String kraPin);

    Page<Customer> findByBlacklistedTrue(Pageable pageable);

    Page<Customer> findByDistributorIdAndBlacklistedTrue(UUID distributorId, Pageable pageable);

    /** Paginated query without active filter (all statuses). */
    Page<Customer> findByDistributorId(UUID distributorId, Pageable pageable);

    /** Scope to a merchant brand (MERCHANT_ADMIN). */
    Page<Customer> findByDistributorMerchantId(UUID merchantId, Pageable pageable);

    Page<Customer> findByDistributorMerchantIdAndActiveTrue(UUID merchantId, Pageable pageable);

    Page<Customer> findByDistributorMerchantIdAndActiveFalse(UUID merchantId, Pageable pageable);

    Page<Customer> findByDistributorMerchantIdAndBlacklistedTrue(UUID merchantId, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.distributor.merchant.id = :merchantId AND (:active IS NULL OR c.active = :active) AND " +
            "(LOWER(c.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.ownerName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "c.phone LIKE CONCAT('%', :searchTerm, '%'))")
    Page<Customer> searchByMerchantAndActive(
            @Param("merchantId") UUID merchantId,
            @Param("searchTerm") String searchTerm,
            @Param("active") Boolean active,
            Pageable pageable);

    @Query("SELECT COUNT(c) FROM Customer c")
    long countAll();

    @Query("SELECT c FROM Customer c WHERE (:active IS NULL OR c.active = :active) AND " +
            "LOWER(c.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Customer> searchByBusinessNameAndActive(@Param("searchTerm") String searchTerm, @Param("active") Boolean active, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.distributor.id = :distributorId AND (:active IS NULL OR c.active = :active) AND " +
            "(LOWER(c.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.ownerName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "c.phone LIKE CONCAT('%', :searchTerm, '%'))")
    Page<Customer> searchByDistributorAndActive(
            @Param("distributorId") UUID distributorId,
            @Param("searchTerm") String searchTerm,
            @Param("active") Boolean active,
            Pageable pageable);

    long countByDistributorIdAndActiveTrue(UUID distributorId);

    long countByAssignedSalesRepIdAndActiveTrue(UUID salesRepId);

    @Query("SELECT DISTINCT c.city FROM Customer c WHERE c.city IS NOT NULL AND c.active = true ORDER BY c.city")
    List<String> findDistinctCities();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.distributor.id = :distributorId AND c.active = true " +
            "AND c.createdAt >= :startDate")
    long countNewCustomersFromDate(@Param("distributorId") UUID distributorId,
            @Param("startDate") java.time.LocalDateTime startDate);

    long countByActiveTrue();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.active = true AND c.createdAt >= :startDate")
    long countNewCustomersFromDateAll(@Param("startDate") java.time.LocalDateTime startDate);

    List<Customer> findByDistributorId(UUID distributorId);

    List<Customer> findByDistributorIdAndActiveTrue(UUID distributorId);

    Page<Customer> findByKycStatus(KycStatus status, Pageable pageable);

    Page<Customer> findByKycStatusNot(KycStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE Customer c SET c.approvalStatus = :status WHERE c.id = :id")
    void updateApprovalStatus(@Param("id") UUID id, @Param("status") String status);
}
