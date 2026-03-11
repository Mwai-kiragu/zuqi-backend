package com.zuqi.repository;

import com.zuqi.domain.ft.FtAmountRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FtAmountRangeRepository extends JpaRepository<FtAmountRange, UUID> {

    List<FtAmountRange> findByDistributorIdAndIsActiveTrueOrderByMinAmountAsc(UUID distributorId);

    // Find the matching amount range for a given transfer amount
    @Query("SELECT r FROM FtAmountRange r WHERE r.distributorId = :distributorId AND r.isActive = true " +
           "AND r.minAmount <= :amount AND (r.maxAmount IS NULL OR r.maxAmount >= :amount) " +
           "ORDER BY r.minAmount DESC")
    List<FtAmountRange> findMatchingRange(@Param("distributorId") UUID distributorId,
                                          @Param("amount") BigDecimal amount);
}
