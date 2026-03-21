package com.zuqi.service;

import com.zuqi.api.dto.approval.TransactionalApprovalResponse;

import java.util.UUID;

public interface TransactionalApprovalService {

    /** Submit an entity for VERIFIER review (INITIATOR / DISTRIBUTOR_ADMIN action) */
    TransactionalApprovalResponse submit(String entityType, UUID entityId, UUID submittedById);

    /** Approve the current pending level (VERIFIER or AUTHORIZER action) */
    TransactionalApprovalResponse approve(String entityType, UUID entityId, UUID approverId, String comment);

    /** Reject at any level (VERIFIER or AUTHORIZER action) */
    TransactionalApprovalResponse reject(String entityType, UUID entityId, UUID approverId, String reason);

    /** Get full approval history for an entity */
    TransactionalApprovalResponse getHistory(String entityType, UUID entityId);
}
