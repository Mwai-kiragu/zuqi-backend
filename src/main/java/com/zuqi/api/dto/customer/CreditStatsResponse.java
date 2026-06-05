package com.zuqi.api.dto.customer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditStatsResponse {
    private BigDecimal totalCreditLimit;
    private BigDecimal totalOutstanding;
    private BigDecimal totalAvailable;
    private long atRiskCount;
}
