package com.zuqi.api.dto.credit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class CreditLimitRequest {

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotNull(message = "Distributor ID is required")
    private UUID distributorId;

    @NotNull(message = "Approved limit is required")
    @Positive(message = "Approved limit must be positive")
    private BigDecimal approvedLimit;

    private BigDecimal interestRate;

    private LocalDateTime expiresAt;
}
