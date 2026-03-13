package com.zuqi.api.dto.aging;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ApAgingResponse {
    private LocalDate asOfDate;
    private List<ApAgingRow> rows;
    private AgingBucketSummary summary;
}
