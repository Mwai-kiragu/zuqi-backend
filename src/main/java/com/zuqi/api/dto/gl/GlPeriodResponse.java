package com.zuqi.api.dto.gl;

import com.zuqi.domain.gl.GlPeriod;
import com.zuqi.domain.gl.GlPeriodStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlPeriodResponse {

    private UUID id;
    private UUID distributorId;
    private String periodName;
    private int periodYear;
    private int periodMonth;
    private LocalDate startDate;
    private LocalDate endDate;
    private GlPeriodStatus status;
    private LocalDateTime closedAt;
    private UUID closedBy;
    private String closedNotes;
    private LocalDateTime lockedAt;
    private UUID lockedBy;
    private int gracePeriodDays;
    private boolean autoLocked;
    private LocalDate autoLockDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GlPeriodResponse fromEntity(GlPeriod p) {
        return GlPeriodResponse.builder()
                .id(p.getId())
                .distributorId(p.getDistributorId())
                .periodName(p.getPeriodName())
                .periodYear(p.getPeriodYear())
                .periodMonth(p.getPeriodMonth())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .status(p.getStatus())
                .closedAt(p.getClosedAt())
                .closedBy(p.getClosedBy())
                .closedNotes(p.getClosedNotes())
                .lockedAt(p.getLockedAt())
                .lockedBy(p.getLockedBy())
                .gracePeriodDays(p.getGracePeriodDays())
                .autoLocked(p.isAutoLocked())
                .autoLockDate(p.getAutoLockDate())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
