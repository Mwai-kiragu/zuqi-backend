package com.zuqi.repository;

import com.zuqi.domain.pos.PosSalePayment;
import com.zuqi.domain.pos.PosSaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PosSalePaymentRepository extends JpaRepository<PosSalePayment, UUID> {

    List<PosSalePayment> findBySaleId(UUID saleId);

    @Query("SELECT p.paymentMethod, COALESCE(SUM(p.amount), 0) FROM PosSalePayment p " +
           "JOIN p.sale s WHERE s.branch.id = :branchId AND s.status = :status " +
           "AND s.createdAt BETWEEN :from AND :to GROUP BY p.paymentMethod")
    List<Object[]> sumByPaymentMethodForBranchStatusAndDateRange(
            @Param("branchId") UUID branchId,
            @Param("status") PosSaleStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT p FROM PosSalePayment p WHERE p.sale.shift.id = :shiftId")
    List<PosSalePayment> findByShiftId(@Param("shiftId") UUID shiftId);
}
