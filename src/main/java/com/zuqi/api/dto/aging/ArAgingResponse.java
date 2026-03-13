package com.zuqi.api.dto.aging;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class ArAgingResponse {
    private LocalDate asOfDate;
    private List<ArAgingRow> rows;
    private AgingBucketSummary summary;
}
