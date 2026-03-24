package com.zuqi.repository;

import com.zuqi.domain.approval.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, UUID> {

    List<ApprovalRecord> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, UUID entityId);

    List<ApprovalRecord> findByApproverIdAndStatus(UUID approverId, String status);
}
