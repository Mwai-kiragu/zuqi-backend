package com.zuqi.repository;

import com.zuqi.domain.credit.CreditScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditScoreRepository extends JpaRepository<CreditScore, UUID> {

    Page<CreditScore> findByMerchantId(UUID merchantId, Pageable pageable);

    @Query("SELECT cs FROM CreditScore cs WHERE cs.merchant.id = :merchantId " +
            "ORDER BY cs.createdAt DESC")
    Optional<CreditScore> findLatestByMerchantId(@Param("merchantId") UUID merchantId);

    @Query("SELECT cs FROM CreditScore cs WHERE cs.merchant.id = :merchantId " +
            "AND cs.validUntil > :now ORDER BY cs.createdAt DESC")
    Optional<CreditScore> findValidScoreByMerchantId(
            @Param("merchantId") UUID merchantId,
            @Param("now") LocalDateTime now);
}
