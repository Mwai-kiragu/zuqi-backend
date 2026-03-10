package com.zuqi.api.dto.gl;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BalanceSheetSection {
    private String name;
    private List<BalanceSheetRow> rows;
    private BigDecimal total;
}
