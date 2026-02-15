package com.zuqi.api.controller;

import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.dashboard.*;
import com.zuqi.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard APIs for statistics and overview data")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard stats", description = "Retrieves role-specific dashboard statistics. If distributorId is null, returns aggregated data (admin only)")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats(
            @Parameter(description = "Distributor ID (optional for admins)") @RequestParam(required = false) UUID distributorId) {

        DashboardStatsResponse stats = dashboardService.getStats(distributorId, null);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/orders/recent")
    @Operation(summary = "Get recent orders", description = "Retrieves recent orders for dashboard display")
    public ResponseEntity<ApiResponse<List<RecentOrderResponse>>> getRecentOrders(
            @Parameter(description = "Distributor ID (optional for admins)") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Number of orders to return") @RequestParam(defaultValue = "10") int limit) {

        List<RecentOrderResponse> orders = dashboardService.getRecentOrders(distributorId, null, limit);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    @GetMapping("/merchants/top")
    @Operation(summary = "Get top merchants", description = "Retrieves top performing merchants by revenue")
    public ResponseEntity<ApiResponse<List<TopMerchantResponse>>> getTopMerchants(
            @Parameter(description = "Distributor ID (optional for admins)") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Number of merchants to return") @RequestParam(defaultValue = "5") int limit) {

        List<TopMerchantResponse> merchants = dashboardService.getTopMerchants(distributorId, limit);
        return ResponseEntity.ok(ApiResponse.success(merchants));
    }

    @GetMapping("/revenue/chart")
    @Operation(summary = "Get revenue chart", description = "Retrieves revenue data for charting")
    public ResponseEntity<ApiResponse<RevenueChartData>> getRevenueChart(
            @Parameter(description = "Distributor ID (optional for admins)") @RequestParam(required = false) UUID distributorId,
            @Parameter(description = "Start date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        RevenueChartData chartData = dashboardService.getRevenueChart(distributorId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(chartData));
    }
}
