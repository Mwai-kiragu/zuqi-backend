package com.zuqi.service;

import com.zuqi.api.dto.dashboard.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DashboardService {

    DashboardStatsResponse getStats(UUID distributorId, UUID userId, UUID branchId, LocalDate startDate, LocalDate endDate);

    List<RecentOrderResponse> getRecentOrders(UUID distributorId, UUID userId, int limit, UUID branchId);

    List<TopMerchantResponse> getTopMerchants(UUID distributorId, int limit);

    RevenueChartData getRevenueChart(UUID distributorId, LocalDate startDate, LocalDate endDate);
}
