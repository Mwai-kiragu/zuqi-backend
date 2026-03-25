package com.zuqi.ai.feature;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Feature record for cash flow prediction.
 *
 * 16 features fed into the XGBoost regressor (target: net_cash_flow for a given day).
 * Computed per (distributor, targetDate) pair, cached 24h in Redis.
 */
public record CashFlowFeatures(
        UUID distributorId,
        LocalDate targetDate,
        double pendingOrdersValue,         // Total KES value of undelivered orders
        double avgDailyCollections7d,      // Avg daily payments received, last 7 days
        double avgDailyCollections30d,     // Avg daily payments received, last 30 days
        double collectionTrend,            // Slope: collections30d – collections7d (positive = improving)
        double overdueReceivablesTotal,    // Total KES overdue > 30 days
        double paymentDueNext7d,           // Total KES due from customers in next 7 days
        double pendingPurchaseOrdersValue, // Total KES of pending POs not yet paid
        double avgDailyExpenses30d,        // Avg daily expense outflows, last 30 days
        double upcomingSupplierPayments,   // Total supplier payments due in next 7 days
        int    dayOfWeek,                  // 1=Monday … 7=Sunday
        int    dayOfMonth,                 // 1–31
        double isPaydayWeek,               // 1.0 if week contains 25th–30th of month
        double isMonthEnd,                 // 1.0 if dayOfMonth >= 25
        double netCashFlow7dAgo,           // Actual net cash flow 7 days ago (lagged feature)
        double netCashFlow30dAgo           // Actual net cash flow 30 days ago (lagged feature)
) {}
