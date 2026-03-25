package com.zuqi.repository;

import com.zuqi.domain.ai.ExpiryRiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpiryRiskScoreRepository extends JpaRepository<ExpiryRiskScore, UUID> {

    List<ExpiryRiskScore> findByDistributorIdAndWarehouseId(UUID distributorId, UUID warehouseId);

    List<ExpiryRiskScore> findByDistributorIdAndRiskTier(UUID distributorId, String riskTier);

    @Query("SELECT ers FROM ExpiryRiskScore ers WHERE ers.distributor.id = :distributorId " +
           "AND ers.expiryDate BETWEEN :from AND :to ORDER BY ers.expiryDate ASC")
    List<ExpiryRiskScore> findByDistributorIdAndExpiryDateBetween(
            @Param("distributorId") UUID distributorId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    Optional<ExpiryRiskScore> findByBatchId(UUID batchId);

    @Query("SELECT ers FROM ExpiryRiskScore ers WHERE ers.distributor.id = :distributorId " +
           "AND ers.riskScore >= :minRisk ORDER BY ers.riskScore DESC")
    List<ExpiryRiskScore> findHighRiskByDistributor(
            @Param("distributorId") UUID distributorId,
            @Param("minRisk") double minRisk);
}
