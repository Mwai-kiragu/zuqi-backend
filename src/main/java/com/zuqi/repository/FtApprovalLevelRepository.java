package com.zuqi.repository;

import com.zuqi.domain.ft.FtApprovalLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FtApprovalLevelRepository extends JpaRepository<FtApprovalLevel, UUID> {

    List<FtApprovalLevel> findByAmountRangeIdOrderByLevelNumber(UUID amountRangeId);

    List<FtApprovalLevel> findByAmountRangeIdAndLevelNumber(UUID amountRangeId, int levelNumber);

    void deleteByAmountRangeId(UUID amountRangeId);
}
