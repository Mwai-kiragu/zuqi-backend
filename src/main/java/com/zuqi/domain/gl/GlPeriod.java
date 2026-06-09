package com.zuqi.domain.gl;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gl_periods", indexes = {
        @Index(name = "idx_gl_periods_distributor", columnList = "distributor_id"),
        @Index(name = "idx_gl_periods_status",      columnList = "status"),
        @Index(name = "idx_gl_periods_year_month",  columnList = "period_year, period_month")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "distributor_id", nullable = false)
    private UUID distributorId;

    @Column(name = "period_name", length = 30, nullable = false)
    private String periodName;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private GlPeriodStatus status = GlPeriodStatus.OPEN;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "grace_period_days", nullable = false)
    @Builder.Default
    private int gracePeriodDays = 5;

    @Column(name = "auto_locked", nullable = false)
    @Builder.Default
    private boolean autoLocked = false;

    @Column(name = "closed_notes", columnDefinition = "TEXT")
    private String closedNotes;

    /** Computed: first day after the grace period when auto-lock triggers. */
    public LocalDate getAutoLockDate() {
        return endDate != null ? endDate.plusDays(gracePeriodDays + 1) : null;
    }

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
