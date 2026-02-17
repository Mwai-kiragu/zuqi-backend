package com.zuqi.repository;

import com.zuqi.domain.ai.DemandForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for demand forecasts.
 *
 * Blueprint: implementation_plan.md Phase 3 Task 3.5
 */
@Repository
public interface DemandForecastRepository extends JpaRepository<DemandForecast, UUID> {

    /**
     * Find forecast for a specific merchant-SKU on a specific date.
     */
    Optional<DemandForecast> findByMerchantIdAndSkuIdAndForecastDate(
            UUID merchantId,
            UUID skuId,
            LocalDate forecastDate
    );

    /**
     * Find all forecasts for a merchant on a specific date.
     */
    List<DemandForecast> findByMerchantIdAndForecastDate(UUID merchantId, LocalDate forecastDate);

    /**
     * Find all forecasts for a distributor on a specific date.
     */
    List<DemandForecast> findByDistributorIdAndForecastDate(UUID distributorId, LocalDate forecastDate);

    /**
     * Delete expired forecasts (cleanup job).
     */
    @Modifying
    @Query("DELETE FROM DemandForecast f WHERE f.expiresAt IS NOT NULL AND f.expiresAt < :now")
    int deleteExpiredForecasts(@Param("now") LocalDateTime now);

    /**
     * Count forecasts for a distributor on a specific date.
     */
    long countByDistributorIdAndForecastDate(UUID distributorId, LocalDate forecastDate);
}
