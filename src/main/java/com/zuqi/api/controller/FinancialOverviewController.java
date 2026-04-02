package com.zuqi.api.controller;

import com.zuqi.api.dto.financial.FinancialOverviewResponse;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.service.FinancialOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/financial-overview")
@RequiredArgsConstructor
public class FinancialOverviewController {

    private final FinancialOverviewService financialOverviewService;

    @GetMapping
    public ResponseEntity<ApiResponse<FinancialOverviewResponse>> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();

        return ResponseEntity.ok(ApiResponse.success(financialOverviewService.getOverview(from, to)));
    }
}
