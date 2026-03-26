package com.zuqi.domain.approvalthreshold;

import com.zuqi.domain.approval.ApprovalWorkflowType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_thresholds")
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "distributor_id")
    private UUID distributorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", length = 50, nullable = false)
    private ApprovalWorkflowType workflowType;

    @Column(name = "min_amount", precision = 15, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "required_approvals", nullable = false)
    @Builder.Default
    private int requiredApprovals = 1;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
