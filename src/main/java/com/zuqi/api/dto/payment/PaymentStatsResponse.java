package com.zuqi.api.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatsResponse {
    private BigDecimal totalAmount;
    private BigDecimal completedAmount;
    private BigDecimal pendingAmount;
    private long count;
}
