package com.zuqi.domain.approval;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "approval_requests", indexes = {
        @Index(name = "idx_approval_requests_status", columnList = "status"),
        @Index(name = "idx_approval_requests_type", columnList = "workflow_type"),
        @Index(name = "idx_approval_requests_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_approval_requests_requester", columnList = "requested_by_id"),
        @Index(name = "idx_approval_requests_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "request_number", nullable = false, unique = true, length = 50)
    private String requestNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", nullable = false, length = 50)
    private ApprovalWorkflowType workflowType;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_name", length = 255)
    private String entityName;

    @Column(name = "requested_by_id", nullable = false)
    private UUID requestedById;

    @Column(name = "requested_by_email", nullable = false)
    private String requestedByEmail;

    @Column(name = "requested_by_name")
    private String requestedByName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_values", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> currentValues = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_values", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> requestedValues = new HashMap<>();

    @Column(name = "required_approvals", nullable = false)
    @Builder.Default
    private Integer requiredApprovals = 1;

    @Column(name = "received_approvals", nullable = false)
    @Builder.Default
    private Integer receivedApprovals = 0;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "approvalRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("actionAt ASC")
    @Builder.Default
    private List<ApprovalAction> actions = new ArrayList<>();

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isFullyApproved() {
        return receivedApprovals >= requiredApprovals;
    }

    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }
}
