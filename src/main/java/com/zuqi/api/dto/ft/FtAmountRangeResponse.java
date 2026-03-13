package com.zuqi.api.dto.ft;

import com.zuqi.domain.ft.FtAmountRange;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class FtAmountRangeResponse {

    private UUID id;
    private UUID distributorId;
    private String name;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private int requiredLevels;
    private boolean isActive;
    private LocalDateTime createdAt;
    private List<FtApprovalLevelDto> approvalLevels;

    public static FtAmountRangeResponse from(FtAmountRange r) {
        return FtAmountRangeResponse.builder()
                .id(r.getId())
                .distributorId(r.getDistributorId())
                .name(r.getName())
                .minAmount(r.getMinAmount())
                .maxAmount(r.getMaxAmount())
                .requiredLevels(r.getRequiredLevels())
                .isActive(r.isActive())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
