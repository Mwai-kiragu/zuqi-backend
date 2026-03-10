package com.zuqi.api.dto.gl;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class GeneralLedgerResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<GeneralLedgerAccountRow> accounts;
}
