package com.zuqi.api.dto.ft;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FtAmountRangeRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Min amount is required")
    @DecimalMin(value = "0", message = "Min amount must be non-negative")
    private BigDecimal minAmount;

    private BigDecimal maxAmount; // null = no upper limit

    @Min(value = 1, message = "At least one approval level is required")
    private int requiredLevels;

    // Optional: approver assignments to create together with the range
    private List<FtApprovalLevelRequest> approvalLevels;

    @Data
    public static class FtApprovalLevelRequest {
        @Min(1)
        private int levelNumber;
        private String levelName;
        @NotNull(message = "Approver user ID is required")
        private java.util.UUID approverUserId;
    }
}
