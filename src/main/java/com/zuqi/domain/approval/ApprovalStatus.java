package com.zuqi.domain.approval;

public enum ApprovalStatus {
    PENDING,
    NOT_REQUIRED,
    PENDING_VERIFIER,
    PENDING_AUTHORIZER,
    APPROVED,
    REJECTED,
    EXPIRED,
    CANCELLED
}
