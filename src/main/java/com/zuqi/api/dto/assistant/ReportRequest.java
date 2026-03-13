package com.zuqi.api.dto.assistant;

import com.zuqi.domain.ai.ReportType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class ReportRequest {

    @NotNull(message = "distributorId is required")
    private UUID distributorId;

    @NotNull(message = "conversationId is required")
    private UUID conversationId;

    @NotNull(message = "reportType is required")
    private ReportType reportType;

    /** Optional parameters, e.g. {"periodDays": 30} */
    private Map<String, Object> params;
}
