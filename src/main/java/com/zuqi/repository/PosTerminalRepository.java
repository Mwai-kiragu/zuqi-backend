package com.zuqi.repository;

import com.zuqi.domain.pos.PosTerminal;
import com.zuqi.domain.pos.PosTerminalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PosTerminalRepository extends JpaRepository<PosTerminal, UUID> {

    List<PosTerminal> findByBranchId(UUID branchId);

    List<PosTerminal> findByBranchIdAndStatus(UUID branchId, PosTerminalStatus status);

    boolean existsByCode(String code);
}
