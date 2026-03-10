package com.zuqi.api.dto.gl;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CashFlowRow {
    private String label;
    private BigDecimal amount;
}
