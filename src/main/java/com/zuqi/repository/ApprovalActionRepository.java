package com.zuqi.repository;

import com.zuqi.domain.approval.ApprovalAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, UUID> {

    List<ApprovalAction> findByApprovalRequestId(UUID approvalRequestId);

    Optional<ApprovalAction> findByApprovalRequestIdAndApproverId(UUID approvalRequestId, UUID approverId);

    boolean existsByApprovalRequestIdAndApproverId(UUID approvalRequestId, UUID approverId);
}
