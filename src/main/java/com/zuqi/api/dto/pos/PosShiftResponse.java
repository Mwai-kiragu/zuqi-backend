package com.zuqi.api.dto.pos;

import com.zuqi.domain.pos.PosShiftStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PosShiftResponse {

    private UUID id;
    private UUID branchId;
    private String branchName;
    private UUID terminalId;
    private String terminalName;
    private UUID cashierId;
    private String cashierName;
    private PosShiftStatus status;
    private BigDecimal openingFloat;
    private BigDecimal closingFloat;
    private BigDecimal expectedCash;
    private String notes;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;

    /** NOT_REQUIRED | PENDING | APPROVED | REJECTED */
    private String reconciliationStatus;
    private UUID reconciledById;
    private LocalDateTime reconciledAt;
}
