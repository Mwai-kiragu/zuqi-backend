package com.zuqi.repository;

import com.zuqi.domain.pos.PosShift;
import com.zuqi.domain.pos.PosShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PosShiftRepository extends JpaRepository<PosShift, UUID> {

    Optional<PosShift> findByBranchIdAndCashierIdAndStatus(UUID branchId, UUID cashierId, PosShiftStatus status);

    List<PosShift> findByBranchIdOrderByCreatedAtDesc(UUID branchId);

    Optional<PosShift> findTopByBranchIdAndCashierIdOrderByCreatedAtDesc(UUID branchId, UUID cashierId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        "UPDATE PosShift s SET s.reconciliationStatus = :status, s.reconciledById = :reconciledById, s.reconciledAt = :reconciledAt WHERE s.id = :id")
    void updateReconciliationStatus(
        @org.springframework.data.repository.query.Param("id") UUID id,
        @org.springframework.data.repository.query.Param("status") String status,
        @org.springframework.data.repository.query.Param("reconciledById") UUID reconciledById,
        @org.springframework.data.repository.query.Param("reconciledAt") java.time.LocalDateTime reconciledAt);
}
