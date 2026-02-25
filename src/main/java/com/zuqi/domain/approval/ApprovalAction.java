package com.zuqi.domain.approval;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_actions", indexes = {
        @Index(name = "idx_approval_actions_request", columnList = "approval_request_id"),
        @Index(name = "idx_approval_actions_approver", columnList = "approver_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_request_id", nullable = false)
    private ApprovalRequest approvalRequest;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Column(name = "approver_email", nullable = false)
    private String approverEmail;

    @Column(name = "approver_name")
    private String approverName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalDecision decision;

    @Column(name = "approval_level", nullable = false)
    @Builder.Default
    private Integer approvalLevel = 1;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "action_at", nullable = false)
    private LocalDateTime actionAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
