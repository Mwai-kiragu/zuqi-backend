package com.zuqi.api.dto.gl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceResponse {

    private String periodName;
    private LocalDate asOfDate;
    private List<TrialBalanceRow> rows;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
}
