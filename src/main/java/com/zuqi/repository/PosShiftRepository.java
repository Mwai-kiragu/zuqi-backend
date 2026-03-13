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
}
