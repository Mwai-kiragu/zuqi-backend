package com.zuqi.repository;

import com.zuqi.domain.pos.PosSale;
import com.zuqi.domain.pos.PosSaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PosSaleRepository extends JpaRepository<PosSale, UUID> {

    Page<PosSale> findByBranchId(UUID branchId, Pageable pageable);

    Page<PosSale> findByBranchIdAndStatus(UUID branchId, PosSaleStatus status, Pageable pageable);

    Page<PosSale> findByStatus(PosSaleStatus status, Pageable pageable);

    Page<PosSale> findByShiftId(UUID shiftId, Pageable pageable);

    Optional<PosSale> findByReceiptNumber(String receiptNumber);

    @Query("SELECT COUNT(s) FROM PosSale s WHERE s.branch.id = :branchId AND s.status = :status " +
           "AND s.createdAt BETWEEN :from AND :to")
    long countByBranchAndStatusAndDateRange(@Param("branchId") UUID branchId,
                                            @Param("status") PosSaleStatus status,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM PosSale s WHERE s.branch.id = :branchId " +
           "AND s.status = :status AND s.createdAt BETWEEN :from AND :to")
    BigDecimal sumTotalByBranchAndStatusAndDateRange(@Param("branchId") UUID branchId,
                                                     @Param("status") PosSaleStatus status,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to);

    List<PosSale> findByBranchIdAndStatusAndCreatedAtBetween(UUID branchId, PosSaleStatus status,
                                                              LocalDateTime from, LocalDateTime to);
}
