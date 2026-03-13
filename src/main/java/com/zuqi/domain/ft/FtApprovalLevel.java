package com.zuqi.domain.ft;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ft_approval_levels", indexes = {
        @Index(name = "idx_ft_approval_levels_range", columnList = "amount_range_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FtApprovalLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "amount_range_id", nullable = false)
    private UUID amountRangeId;

    @Column(name = "level_number", nullable = false)
    private int levelNumber;

    @Column(name = "level_name", length = 100)
    private String levelName;

    @Column(name = "approver_user_id", nullable = false)
    private UUID approverUserId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
