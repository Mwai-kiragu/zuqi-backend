package com.zuqi.repository;

import com.zuqi.domain.approval.ApprovalWorkflowConfig;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalWorkflowConfigRepository extends JpaRepository<ApprovalWorkflowConfig, UUID> {

    List<ApprovalWorkflowConfig> findByDistributorIdOrderByWorkflowTypeAscLevelNumberAsc(UUID distributorId);

    List<ApprovalWorkflowConfig> findByDistributorIdAndWorkflowTypeAndActiveTrueOrderByLevelNumberAsc(
            UUID distributorId, ApprovalWorkflowType workflowType);

    int countByDistributorIdAndWorkflowTypeAndActiveTrue(UUID distributorId, ApprovalWorkflowType workflowType);

    boolean existsByDistributorIdAndWorkflowTypeAndLevelNumber(
            UUID distributorId, ApprovalWorkflowType workflowType, Integer levelNumber);
}
