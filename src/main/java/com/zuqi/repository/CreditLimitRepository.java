package com.zuqi.repository;

import com.zuqi.domain.credit.CreditLimit;
import com.zuqi.domain.credit.CreditLimitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CreditLimit entity.
 */
@Repository
public interface CreditLimitRepository extends JpaRepository<CreditLimit, UUID> {

    Optional<CreditLimit> findByMerchantIdAndDistributorIdAndStatus(
            UUID merchantId, UUID distributorId, CreditLimitStatus status);

    Page<CreditLimit> findByDistributorId(UUID distributorId, Pageable pageable);

    Page<CreditLimit> findByMerchantId(UUID merchantId, Pageable pageable);

    Page<CreditLimit> findByDistributorIdAndStatus(UUID distributorId, CreditLimitStatus status, Pageable pageable);

    @Query("SELECT cl FROM CreditLimit cl WHERE cl.distributor.id = :distributorId " +
            "AND (:merchantId IS NULL OR cl.merchant.id = :merchantId) " +
            "AND (:status IS NULL OR cl.status = :status)")
    Page<CreditLimit> findByFilters(
            @Param("distributorId") UUID distributorId,
            @Param("merchantId") UUID merchantId,
            @Param("status") CreditLimitStatus status,
            Pageable pageable);

    @Query("SELECT cl FROM CreditLimit cl WHERE cl.distributor.id = :distributorId " +
            "AND (cl.merchant.businessName LIKE %:search% OR cl.merchant.phone LIKE %:search%)")
    Page<CreditLimit> searchCreditLimits(
            @Param("distributorId") UUID distributorId,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT COUNT(cl) FROM CreditLimit cl WHERE cl.distributor.id = :distributorId " +
            "AND cl.status = :status")
    long countByDistributorIdAndStatus(
            @Param("distributorId") UUID distributorId,
            @Param("status") CreditLimitStatus status);
}
