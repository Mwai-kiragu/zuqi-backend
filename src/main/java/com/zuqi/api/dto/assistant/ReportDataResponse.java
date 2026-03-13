package com.zuqi.api.dto.assistant;

import com.zuqi.domain.ai.ReportType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ReportDataResponse {
    private ReportType type;
    private UUID distributorId;
    private LocalDateTime generatedAt;
    private int periodDays;
    private Map<String, Object> data;
}
