package com.zuqi.api.dto.aging;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AgingBucketSummary {
    private BigDecimal current;     // not yet due
    private BigDecimal bucket1;     // 1-30 days
    private BigDecimal bucket2;     // 31-60 days
    private BigDecimal bucket3;     // 61-90 days
    private BigDecimal bucket4;     // 90+ days
    private BigDecimal total;
}
