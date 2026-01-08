package com.zuqi.repository;

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

/**
 * Repository for Merchant entity operations.
 */
@Repository
public interface MerchantRepository extends JpaRepository<Merchant, UUID>, JpaSpecificationExecutor<Merchant> {

    /**
     * Find all active merchants.
     */
    Page<Merchant> findByActiveTrue(Pageable pageable);

    /**
     * Find merchants by distributor ID.
     */
    Page<Merchant> findByDistributorIdAndActiveTrue(UUID distributorId, Pageable pageable);

    /**
     * Find merchants by assigned sales rep.
     */
    Page<Merchant> findByAssignedSalesRepIdAndActiveTrue(UUID salesRepId, Pageable pageable);

    /**
     * Find merchants by category.
     */
    Page<Merchant> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    /**
     * Find merchant by email.
     */
    Optional<Merchant> findByEmail(String email);

    /**
     * Find merchant by phone.
     */
    Optional<Merchant> findByPhone(String phone);

    /**
     * Check if merchant exists by phone.
     */
    boolean existsByPhone(String phone);

    /**
     * Check if merchant exists by email.
     */
    boolean existsByEmail(String email);

    /**
     * Search merchants by business name.
     */
    @Query("SELECT m FROM Merchant m WHERE m.active = true AND " +
            "LOWER(m.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Merchant> searchByBusinessName(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find merchants by distributor and search term.
     */
    @Query("SELECT m FROM Merchant m WHERE m.distributor.id = :distributorId AND m.active = true AND " +
            "(LOWER(m.businessName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(m.ownerName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "m.phone LIKE CONCAT('%', :searchTerm, '%'))")
    Page<Merchant> searchByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable);

    /**
     * Count merchants by distributor.
     */
    long countByDistributorIdAndActiveTrue(UUID distributorId);

    /**
     * Count merchants by sales rep.
     */
    long countByAssignedSalesRepIdAndActiveTrue(UUID salesRepId);

    /**
     * Find merchants by city.
     */
    Page<Merchant> findByCityAndActiveTrue(String city, Pageable pageable);

    /**
     * Get distinct cities.
     */
    @Query("SELECT DISTINCT m.city FROM Merchant m WHERE m.city IS NOT NULL AND m.active = true ORDER BY m.city")
    List<String> findDistinctCities();

    /**
     * Count new merchants this month.
     */
    @Query("SELECT COUNT(m) FROM Merchant m WHERE m.distributor.id = :distributorId AND m.active = true " +
            "AND m.createdAt >= :startDate")
    long countNewMerchantsFromDate(@Param("distributorId") UUID distributorId,
            @Param("startDate") java.time.LocalDateTime startDate);
}
