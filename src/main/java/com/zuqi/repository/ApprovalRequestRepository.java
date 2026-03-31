package com.zuqi.repository;

import com.zuqi.domain.approval.ApprovalRequest;
import com.zuqi.domain.approval.ApprovalStatus;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    Optional<ApprovalRequest> findByRequestNumber(String requestNumber);

    Page<ApprovalRequest> findByStatus(ApprovalStatus status, Pageable pageable);

    Page<ApprovalRequest> findByRequestedById(UUID requestedById, Pageable pageable);

    Page<ApprovalRequest> findByWorkflowType(ApprovalWorkflowType workflowType, Pageable pageable);

    List<ApprovalRequest> findByEntityTypeAndEntityIdAndStatus(
            String entityType, UUID entityId, ApprovalStatus status);

    @Query("""
            SELECT ar FROM ApprovalRequest ar
            WHERE ar.status = 'PENDING'
            AND ar.expiresAt IS NOT NULL
            AND ar.expiresAt < :now
            """)
    List<ApprovalRequest> findExpiredRequests(@Param("now") LocalDateTime now);

    @Query("""
            SELECT ar FROM ApprovalRequest ar
            WHERE (:status IS NULL OR ar.status = :status)
            AND (:workflowType IS NULL OR ar.workflowType = :workflowType)
            AND (:entityType IS NULL OR ar.entityType = :entityType)
            ORDER BY ar.createdAt DESC
            """)
    Page<ApprovalRequest> findWithFilters(
            @Param("status") ApprovalStatus status,
            @Param("workflowType") ApprovalWorkflowType workflowType,
            @Param("entityType") String entityType,
            Pageable pageable);

    Page<ApprovalRequest> findByStatusAndDistributorId(ApprovalStatus status, UUID distributorId, Pageable pageable);

    long countByStatus(ApprovalStatus status);

    long countByStatusAndDistributorId(ApprovalStatus status, UUID distributorId);

    long countByRequestedByIdAndStatus(UUID requestedById, ApprovalStatus status);
}
