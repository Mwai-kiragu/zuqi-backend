package com.zuqi.repository;

import com.zuqi.domain.ai.CashFlowForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for cash flow forecasts.
 * Supports date-range retrieval and forward-looking projection queries.
 */
@Repository
public interface CashFlowForecastRepository extends JpaRepository<CashFlowForecast, UUID> {

    /**
     * Find all forecasts for a distributor within an inclusive date range.
     */
    List<CashFlowForecast> findByDistributorIdAndForecastDateBetween(
            UUID distributorId,
            LocalDate startDate,
            LocalDate endDate);

    /**
     * Find all future forecasts for a distributor after a given date.
     */
    List<CashFlowForecast> findByDistributorIdAndForecastDateAfter(
            UUID distributorId,
            LocalDate afterDate);

    /**
     * Find the single forecast for a distributor on an exact date.
     */
    Optional<CashFlowForecast> findByDistributorIdAndForecastDate(
            UUID distributorId,
            LocalDate forecastDate);
}
