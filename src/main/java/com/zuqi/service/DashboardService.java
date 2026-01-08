package com.zuqi.service;

import com.zuqi.api.dto.dashboard.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for dashboard operations.
 */
public interface DashboardService {

    /**
     * Get dashboard statistics for a distributor.
     * Results are role-filtered to show relevant stats.
     */
    DashboardStatsResponse getStats(UUID distributorId, UUID userId);

    /**
     * Get recent orders for dashboard display.
     */
    List<RecentOrderResponse> getRecentOrders(UUID distributorId, UUID userId, int limit);

    /**
     * Get top performing merchants.
     */
    List<TopMerchantResponse> getTopMerchants(UUID distributorId, int limit);

    /**
     * Get revenue chart data.
     */
    RevenueChartData getRevenueChart(UUID distributorId, LocalDate startDate, LocalDate endDate);
}
