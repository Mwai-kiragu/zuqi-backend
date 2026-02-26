package com.zuqi.repository;

import com.zuqi.domain.merchant.KycStatus;
import com.zuqi.domain.merchant.Merchant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID>, JpaSpecificationExecutor<Merchant> {

    Page<Merchant> findByActiveTrue(Pageable pageable);

    Page<Merchant> findByDistributorIdAndActiveTrue(UUID distributorId, Pageable pageable);

    Page<Merchant> findByActiveFalse(Pageable pageable);

    Page<Merchant> findByDistributorIdAndActiveFalse(UUID distributorId, Pageable pageable);

    Page<Merchant> findByAssignedSalesRepIdAndActiveTrue(UUID salesRepId, Pageable pageable);

    Page<Merchant> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    Optional<Merchant> findByEmail(String email);

    Optional<Merchant> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByKraPin(String kraPin);

    boolean existsByCategoryId(Long categoryId);

    Optional<Merchant> findByCustomerCode(String customerCode);

    Optional<Merchant> findByKraPin(String kraPin);

    Page<Merchant> findByBlacklistedTrue(Pageable pageable);

    Page<Merchant> findByDistributorIdAndBlacklistedTrue(UUID distributorId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM Merchant m")
    long countAll();

    @Query("SELECT m FROM Merchant m WHERE m.active = true AND " +
            "LOWER(m.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Merchant> searchByBusinessName(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT m FROM Merchant m WHERE m.active = :active AND " +
            "LOWER(m.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Merchant> searchByBusinessNameAndActive(@Param("searchTerm") String searchTerm, @Param("active") Boolean active, Pageable pageable);

    @Query("SELECT m FROM Merchant m WHERE m.distributor.id = :distributorId AND m.active = true AND " +
            "(LOWER(m.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(m.ownerName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "m.phone LIKE CONCAT('%', :searchTerm, '%'))")
    Page<Merchant> searchByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    @Query("SELECT m FROM Merchant m WHERE m.distributor.id = :distributorId AND m.active = :active AND " +
            "(LOWER(m.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(m.ownerName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "m.phone LIKE CONCAT('%', :searchTerm, '%'))")
    Page<Merchant> searchByDistributorAndActive(
            @Param("distributorId") UUID distributorId,
            @Param("searchTerm") String searchTerm,
            @Param("active") Boolean active,
            Pageable pageable);

    long countByDistributorIdAndActiveTrue(UUID distributorId);

    long countByAssignedSalesRepIdAndActiveTrue(UUID salesRepId);

    Page<Merchant> findByCityAndActiveTrue(String city, Pageable pageable);

    @Query("SELECT DISTINCT m.city FROM Merchant m WHERE m.city IS NOT NULL AND m.active = true ORDER BY m.city")
    List<String> findDistinctCities();

    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.distributor.id = :distributorId AND m.active = true " +
            "AND m.createdAt >= :startDate")
    long countNewMerchantsFromDate(@Param("distributorId") UUID distributorId,
            @Param("startDate") java.time.LocalDateTime startDate);

    long countByActiveTrue();

    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.active = true AND m.createdAt >= :startDate")
    long countNewMerchantsFromDateAll(@Param("startDate") java.time.LocalDateTime startDate);

    /**
     * Find all merchants for a distributor (for batch operations).
     */
    List<Merchant> findByDistributorId(UUID distributorId);

    /**
     * Find all active merchants for a distributor (for batch operations).
     */
    List<Merchant> findByDistributorIdAndActiveTrue(UUID distributorId);

    Page<Merchant> findByKycStatus(KycStatus status, Pageable pageable);

    Page<Merchant> findByKycStatusNot(KycStatus status, Pageable pageable);
}
