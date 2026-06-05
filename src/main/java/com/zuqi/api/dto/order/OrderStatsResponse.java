package com.zuqi.api.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatsResponse {
    private long totalCount;
    private BigDecimal totalAmount;
    private long paidCount;
    private BigDecimal paidAmount;
    private long unpaidCount;
    private BigDecimal unpaidAmount;
    private long partialCount;
    private BigDecimal partialBalanceDue;
}
