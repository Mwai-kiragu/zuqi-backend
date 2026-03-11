package com.zuqi.repository;

import com.zuqi.domain.ft.FtApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FtApprovalRepository extends JpaRepository<FtApproval, UUID> {

    List<FtApproval> findByTransferIdOrderByLevelNumberAscCreatedAtAsc(UUID transferId);

    List<FtApproval> findByTransferIdAndLevelNumber(UUID transferId, int levelNumber);

    Optional<FtApproval> findByTransferIdAndLevelNumberAndApproverId(UUID transferId, int levelNumber, UUID approverId);

    long countByTransferIdAndLevelNumberAndStatus(UUID transferId, int levelNumber, String status);
}
