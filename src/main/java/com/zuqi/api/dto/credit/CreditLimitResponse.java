package com.zuqi.api.dto.credit;

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
public class CreditLimitResponse {

    private UUID id;
    private UUID merchantId;
    private String merchantName;
    private String merchantPhone;
    private UUID distributorId;
    private String distributorName;
    private BigDecimal approvedLimit;
    private BigDecimal utilizedAmount;
    private BigDecimal availableLimit;
    private BigDecimal interestRate;
    private String status;
    private UUID approvedById;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
