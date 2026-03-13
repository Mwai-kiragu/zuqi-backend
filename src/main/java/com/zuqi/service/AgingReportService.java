package com.zuqi.service;

import com.zuqi.api.dto.aging.ApAgingResponse;
import com.zuqi.api.dto.aging.ArAgingResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface AgingReportService {
    ArAgingResponse getArAging(UUID distributorId, LocalDate asOfDate);
    ApAgingResponse getApAging(UUID distributorId, LocalDate asOfDate);
}
