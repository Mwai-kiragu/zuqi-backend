package com.zuqi.api.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dashboard statistics response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    // Order statistics
    private Long totalOrders;
    private Long ordersToday;
    private Long pendingOrders;
    private Long processingOrders;
    private Long deliveredOrders;

    // Revenue statistics
    private BigDecimal totalRevenue;
    private BigDecimal revenueToday;
    private BigDecimal revenueThisMonth;

    // Merchant statistics
    private Long totalMerchants;
    private Long activeMerchants;
    private Long newMerchantsThisMonth;

    // Payment statistics
    private Long pendingPayments;
    private Long unreconciledPayments;
    private BigDecimal totalOutstanding;

    // Inventory statistics
    private Long lowStockProducts;
    private Long outOfStockProducts;

    // Delivery statistics
    private Long pendingDeliveries;
    private Long outForDelivery;

    // Sales team statistics
    private Long activeSalesReps;
    private Long totalSalesReps;
}
