package com.zuqi.repository;

import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.approvalthreshold.ApprovalThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalThresholdRepository extends JpaRepository<ApprovalThreshold, UUID> {

    List<ApprovalThreshold> findByDistributorIdAndWorkflowTypeAndActiveTrue(
            UUID distributorId, ApprovalWorkflowType workflowType);

    List<ApprovalThreshold> findByDistributorIdAndActiveTrue(UUID distributorId);

    @Query("SELECT t FROM ApprovalThreshold t WHERE " +
           "(:distributorId IS NULL AND t.distributorId IS NULL OR t.distributorId = :distributorId) " +
           "AND t.workflowType = :type AND t.active = true " +
           "AND t.minAmount <= :amount AND (t.maxAmount IS NULL OR t.maxAmount >= :amount) " +
           "ORDER BY t.minAmount DESC")
    List<ApprovalThreshold> findMatchingThresholds(
            @Param("distributorId") UUID distributorId,
            @Param("type") ApprovalWorkflowType type,
            @Param("amount") BigDecimal amount);
}
