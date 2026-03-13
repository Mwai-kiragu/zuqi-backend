package com.zuqi.api.dto.gl;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProfitLossSection {
    private String name;
    private List<ProfitLossRow> rows;
    private BigDecimal total;
}
