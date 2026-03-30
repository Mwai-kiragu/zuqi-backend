package com.zuqi.domain.approval;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_workflow_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ApprovalWorkflowConfig {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** Scoped to a specific distributor; null means system-level default. */
    private UUID distributorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private ApprovalWorkflowType workflowType;

    /** Approval level position (1 = first approver, 2 = second, etc.). */
    @Column(nullable = false)
    private Integer levelNumber;

    /** Human-readable label, e.g. "Accountant", "General Manager", "Directors". */
    @Column(nullable = false, length = 100)
    private String roleLabel;

    /** Optional Casbin role key, e.g. FINANCE, SALES_REP — informational only. */
    @Column(length = 60)
    private String requiredRole;

    @Builder.Default
    private boolean active = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
