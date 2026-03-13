package com.zuqi.api.dto.gl;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CashFlowResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private CashFlowSection operatingActivities;
    private CashFlowSection investingActivities;
    private CashFlowSection financingActivities;
    private BigDecimal netCashChange;
}
