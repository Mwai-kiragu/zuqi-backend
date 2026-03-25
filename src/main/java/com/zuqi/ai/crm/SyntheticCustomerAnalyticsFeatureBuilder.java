package com.zuqi.ai.crm;

import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import com.zuqi.ai.synthetic.dto.SyntheticOrder;
import com.zuqi.ai.synthetic.dto.SyntheticPayment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds {@link CustomerAnalyticsFeatures} from in-memory synthetic data.
 *
 * <p>Mirrors the logic of {@link CustomerAnalyticsFeatureServiceImpl} so that training
 * and inference use identical feature computation paths — only the data source differs.
 */
@Component
@Slf4j
public class SyntheticCustomerAnalyticsFeatureBuilder {

    /**
     * Compute {@link CustomerAnalyticsFeatures} for a synthetic merchant.
     *
     * @param merchant the merchant to compute features for
     * @param bundle   full in-memory synthetic dataset
     * @param asOf     reference point ("now") for window calculations
     * @return populated features record
     */
    public CustomerAnalyticsFeatures computeFeatures(SyntheticMerchant merchant,
                                                      SyntheticDataBundle bundle,
                                                      LocalDateTime asOf) {
        UUID mid = merchant.syntheticId();
        List<SyntheticOrder> allOrders = bundle.getOrdersForMerchant(mid);
        List<SyntheticPayment> payments = bundle.getPaymentsForMerchant(mid);

        // Active orders (non-CANCELLED)
        List<SyntheticOrder> activeOrders = allOrders.stream()
                .filter(o -> !"CANCELLED".equals(o.status()))
                .collect(Collectors.toList());

        // Revenue windows
        double totalRevenue90d = revenueWindow(activeOrders, asOf, 90);
        double revenue30d = revenueWindow(activeOrders, asOf, 30);
        double revenue30to60d = revenueWindowBetween(activeOrders, asOf, 30, 60);
        double revenue3m = totalRevenue90d; // 90 days ≈ 3 months
        double revenue6m = revenueWindow(activeOrders, asOf, 180);
        double revenue12m = revenueWindow(activeOrders, asOf, 365);
        double lifetimeRevenue = activeOrders.stream()
                .mapToDouble(o -> o.totalAmount() != null ? o.totalAmount().doubleValue() : 0.0)
                .sum();

        // Order counts
        double orderCount90d = countInWindow(activeOrders, asOf, 90);
        double orderCount30d = countInWindow(activeOrders, asOf, 30);

        // Frequency: 90 days / 7 days per week ≈ 13 weeks
        double orderFrequencyPerWeek = orderCount90d / 13.0;

        // Avg order value
        double avgOrderValue = activeOrders.isEmpty() ? 0.0
                : activeOrders.stream()
                        .mapToDouble(o -> o.totalAmount() != null ? o.totalAmount().doubleValue() : 0.0)
                        .average()
                        .orElse(0.0);

        // Revenue trend slope
        double revenueTrendSlope = revenue30to60d > 0
                ? (revenue30d - revenue30to60d) / revenue30to60d
                : (revenue30d > 0 ? 1.0 : 0.0);

        // Payment timeliness: on-time = daysAfterInvoice <= 30
        double paymentTimelinessScore = computePaymentTimeliness(payments);

        // Credit utilization proxy
        BigDecimal creditLimit = merchant.initialCreditLimit();
        double creditUtilizationPct = 0.0;
        if (creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) > 0) {
            // Approximate outstanding as 30% of last 30d delivered order values
            double outstanding30d = activeOrders.stream()
                    .filter(o -> "DELIVERED".equals(o.status()))
                    .filter(o -> o.orderDate() != null && o.orderDate().isAfter(asOf.minusDays(30)))
                    .mapToDouble(o -> o.totalAmount() != null ? o.totalAmount().doubleValue() * 0.3 : 0.0)
                    .sum();
            creditUtilizationPct = Math.min(100.0,
                    outstanding30d / creditLimit.doubleValue() * 100.0);
        }

        // Days since last order
        int daysSinceLastOrder = activeOrders.stream()
                .map(SyntheticOrder::orderDate)
                .max(LocalDateTime::compareTo)
                .map(last -> (int) ChronoUnit.DAYS.between(last, asOf))
                .orElse(Integer.MAX_VALUE);

        // Product diversity proxy
        double productDiversityScore = orderCount90d > 0 ? Math.min(1.0, orderCount90d / 10.0) : 0.0;

        // Tenure in months
        int tenureMonths = merchant.registrationDate() != null
                ? (int) ChronoUnit.MONTHS.between(merchant.registrationDate(), asOf.toLocalDate())
                : 0;

        String customerCategory = merchant.businessCategory() != null
                ? merchant.businessCategory()
                : "UNKNOWN";

        return new CustomerAnalyticsFeatures(
                mid,
                null, // distributorId — not needed during training
                totalRevenue90d,
                lifetimeRevenue,
                revenue3m,
                revenue6m,
                revenue12m,
                orderFrequencyPerWeek,
                avgOrderValue,
                revenueTrendSlope,
                paymentTimelinessScore,
                creditUtilizationPct,
                daysSinceLastOrder,
                productDiversityScore,
                tenureMonths,
                customerCategory,
                orderCount30d,
                orderCount90d
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double revenueWindow(List<SyntheticOrder> orders, LocalDateTime asOf, int days) {
        LocalDateTime cutoff = asOf.minusDays(days);
        return orders.stream()
                .filter(o -> o.orderDate() != null && o.orderDate().isAfter(cutoff))
                .mapToDouble(o -> o.totalAmount() != null ? o.totalAmount().doubleValue() : 0.0)
                .sum();
    }

    private double revenueWindowBetween(List<SyntheticOrder> orders, LocalDateTime asOf, int from, int to) {
        LocalDateTime start = asOf.minusDays(to);
        LocalDateTime end = asOf.minusDays(from);
        return orders.stream()
                .filter(o -> o.orderDate() != null
                        && o.orderDate().isAfter(start)
                        && o.orderDate().isBefore(end))
                .mapToDouble(o -> o.totalAmount() != null ? o.totalAmount().doubleValue() : 0.0)
                .sum();
    }

    private double countInWindow(List<SyntheticOrder> orders, LocalDateTime asOf, int days) {
        LocalDateTime cutoff = asOf.minusDays(days);
        return orders.stream()
                .filter(o -> o.orderDate() != null && o.orderDate().isAfter(cutoff))
                .count();
    }

    private double computePaymentTimeliness(List<SyntheticPayment> payments) {
        if (payments.isEmpty()) return 100.0;
        long onTime = payments.stream()
                .filter(p -> p.daysAfterInvoice() <= 30)
                .count();
        return (double) onTime / payments.size() * 100.0;
    }
}
