package com.zuqi.api.dto.gl;

import com.zuqi.domain.gl.Budget;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetResponse {

    private UUID id;
    private UUID distributorId;
    private int budgetYear;
    private int periodMonth;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private UUID costCenterId;
    private String costCenterCode;
    private String costCenterName;
    private BigDecimal budgetedAmount;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BudgetResponse fromEntity(Budget b) {
        return BudgetResponse.builder()
                .id(b.getId())
                .distributorId(b.getDistributorId())
                .budgetYear(b.getBudgetYear())
                .periodMonth(b.getPeriodMonth())
                .accountId(b.getAccount() != null ? b.getAccount().getId() : null)
                .accountCode(b.getAccount() != null ? b.getAccount().getAccountCode() : null)
                .accountName(b.getAccount() != null ? b.getAccount().getAccountName() : null)
                .costCenterId(b.getCostCenter() != null ? b.getCostCenter().getId() : null)
                .costCenterCode(b.getCostCenter() != null ? b.getCostCenter().getCode() : null)
                .costCenterName(b.getCostCenter() != null ? b.getCostCenter().getName() : null)
                .budgetedAmount(b.getBudgetedAmount())
                .notes(b.getNotes())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}
