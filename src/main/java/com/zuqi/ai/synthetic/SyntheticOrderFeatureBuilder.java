package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.ai.feature.DemandFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds {@link DemandFeatures} for a merchant-SKU pair from in-memory synthetic data.
 *
 * <p>Computation logic mirrors {@link com.zuqi.ai.feature.OrderFeatureServiceImpl}
 * exactly — only the data source differs.
 */
@Component
@Slf4j
public class SyntheticOrderFeatureBuilder {

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Compute demand features for a specific merchant-SKU combination.
     *
     * @param merchant  the synthetic merchant
     * @param skuId     the product/SKU UUID to compute demand for
     * @param bundle    the full in-memory dataset
     * @param asOfDate  reference date ("now" for training purposes)
     * @return fully populated {@link DemandFeatures}
     */
    public DemandFeatures computeFeatures(SyntheticMerchant merchant,
                                           UUID skuId,
                                           SyntheticDataBundle bundle,
                                           LocalDateTime asOfDate) {
        UUID mid = merchant.syntheticId();
        List<SyntheticOrder> allOrders = bundle.getOrdersForMerchant(mid);

        // Collect orders that contain this SKU, sorted ascending for trend computation
        List<SyntheticOrder> skuOrders = allOrders.stream()
                .filter(o -> bundle.getItemsForOrder(o.syntheticId()).stream()
                        .anyMatch(item -> item.skuId().equals(skuId)))
                .sorted(Comparator.comparing(SyntheticOrder::orderDate))
                .collect(Collectors.toList());

        // Lag features — calendar-week aligned, matching OrderFeatureServiceImpl#getQuantityNWeeksAgo
        BigDecimal qty1w = qtyInCalendarWeek(skuId, bundle, asOfDate, 1);
        BigDecimal qty2w = qtyInCalendarWeek(skuId, bundle, asOfDate, 2);
        BigDecimal qty3w = qtyInCalendarWeek(skuId, bundle, asOfDate, 3);
        BigDecimal qty4w = qtyInCalendarWeek(skuId, bundle, asOfDate, 4);

        // Rolling averages — total qty in window / weeks, matching OrderFeatureServiceImpl#getRollingAverage
        BigDecimal rolling4w  = computeRollingAvg(skuId, skuOrders, bundle, asOfDate, 4);
        BigDecimal rolling12w = computeRollingAvg(skuId, skuOrders, bundle, asOfDate, 12);

        // Trend direction — 4w vs 12w rolling avg, 15% threshold, matching real service
        String trendDirection = computeTrendDirection(rolling4w, rolling12w);

        LocalDate asOf = asOfDate.toLocalDate();

        // merchantSizeTier — count-based (last 12 weeks), matching OrderFeatureServiceImpl#computeMerchantSizeTier
        String sizeTier = computeMerchantSizeTier(allOrders, asOfDate);

        // merchantCreditStatus — payment on-time rate, matching OrderFeatureServiceImpl#computeMerchantCreditStatus
        String creditStatus = computeCreditStatus(bundle.getPaymentsForMerchant(mid));

        // priceTier — computed from typical unit price for this SKU, matching OrderFeatureServiceImpl#computePriceTier
        String priceTier = computePriceTier(skuId, bundle);

        log.debug("[SyntheticOrderFB] merchant={} sku={} skuOrders={} trendDir={}",
                mid, skuId, skuOrders.size(), trendDirection);

        return DemandFeatures.builder()
                .merchantId(mid)
                .productId(skuId)
                .computedAt(asOfDate)
                // Lag features
                .qty1wAgo(qty1w)
                .qty2wAgo(qty2w)
                .qty3wAgo(qty3w)
                .qty4wAgo(qty4w)
                .rollingAvg4w(rolling4w)
                .rollingAvg12w(rolling12w)
                .trendDirection(trendDirection)
                // Temporal features
                .dayOfWeek(asOf.getDayOfWeek().getValue())
                .weekOfMonth((asOf.getDayOfMonth() - 1) / 7 + 1)
                .monthOfYear(asOf.getMonthValue())
                .isHoliday(isHoliday(asOf))
                .isPaydayWeek(isPaydayWeek(asOf))
                .isRamadan(isRamadan(asOf))
                .isChristmasSeason(asOf.getMonthValue() == 11 || asOf.getMonthValue() == 12)
                // Merchant context
                .merchantCategory(merchant.businessCategory())
                .merchantSizeTier(sizeTier)
                .merchantCreditStatus(creditStatus)
                .merchantTenureDays((int) ChronoUnit.DAYS.between(merchant.registrationDate(), asOf))
                // SKU context — productCategory defaults to "UNKNOWN" (matching real service null-category default);
                // priceTier derived from unit price; isPromotional=false (no discount data in synthetic);
                // typicalShelfLifeDays=60 (matching real service default for unknown category)
                .productCategory("UNKNOWN")
                .priceTier(priceTier)
                .isPromotional(false)
                .typicalShelfLifeDays(60)
                .build();
    }

    // ── Lag / rolling helpers ──────────────────────────────────────────────

    /**
     * Sum the quantity of {@code skuId} ordered during the calendar week (Mon–Sun)
     * that ended {@code weeksAgo} weeks before {@code asOfDate}.
     *
     * Mirrors {@code OrderFeatureServiceImpl#getQuantityNWeeksAgo} exactly.
     */
    private BigDecimal qtyInCalendarWeek(UUID skuId, SyntheticDataBundle bundle,
                                          LocalDateTime asOfDate, int weeksAgo) {
        LocalDate startOfCalendarWeek = asOfDate.toLocalDate()
                .minusWeeks(weeksAgo)
                .with(DayOfWeek.MONDAY);
        LocalDate endOfCalendarWeek = startOfCalendarWeek.plusDays(7);

        return bundle.getOrders().stream()
                .filter(o -> {
                    LocalDate orderDay = o.orderDate().toLocalDate();
                    return !orderDay.isBefore(startOfCalendarWeek) && orderDay.isBefore(endOfCalendarWeek);
                })
                .flatMap(o -> bundle.getItemsForOrder(o.syntheticId()).stream())
                .filter(item -> item.skuId().equals(skuId))
                .map(SyntheticOrderItem::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Average weekly quantity over a rolling {@code weeks}-week window ending at {@code asOfDate}.
     *
     * Mirrors {@code OrderFeatureServiceImpl#getRollingAverage}: total qty in window / weeks.
     */
    private BigDecimal computeRollingAvg(UUID skuId,
                                          List<SyntheticOrder> skuOrders,
                                          SyntheticDataBundle bundle,
                                          LocalDateTime asOfDate,
                                          int weeks) {
        LocalDateTime cutoff = asOfDate.minusWeeks(weeks);
        BigDecimal totalQty = skuOrders.stream()
                .filter(o -> o.orderDate().isAfter(cutoff))
                .flatMap(o -> bundle.getItemsForOrder(o.syntheticId()).stream())
                .filter(item -> item.skuId().equals(skuId))
                .map(SyntheticOrderItem::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalQty.divide(BigDecimal.valueOf(weeks), 2, RoundingMode.HALF_UP);
    }

    /**
     * Trend direction: compare 4-week vs 12-week rolling averages using 15% threshold.
     *
     * Mirrors {@code OrderFeatureServiceImpl#computeTrendDirection} exactly.
     */
    private String computeTrendDirection(BigDecimal avg4w, BigDecimal avg12w) {
        if (avg4w.compareTo(BigDecimal.ZERO) == 0 && avg12w.compareTo(BigDecimal.ZERO) == 0) {
            return "STABLE";
        }
        BigDecimal threshold = BigDecimal.valueOf(0.15);
        BigDecimal diff = avg12w.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                avg4w.subtract(avg12w).divide(avg12w, 4, RoundingMode.HALF_UP);
        if (diff.compareTo(threshold) > 0) return "INCREASING";
        if (diff.compareTo(threshold.negate()) < 0) return "DECREASING";
        return "STABLE";
    }

    // ── Merchant size tier ─────────────────────────────────────────────────

    /**
     * Classify merchant by order COUNT in last 12 weeks.
     *
     * Mirrors {@code OrderFeatureServiceImpl#computeMerchantSizeTier} exactly.
     */
    private String computeMerchantSizeTier(List<SyntheticOrder> allOrders, LocalDateTime asOfDate) {
        long recentOrderCount = allOrders.stream()
                .filter(o -> o.orderDate().isAfter(asOfDate.minusWeeks(12)))
                .count();
        if (recentOrderCount >= 20) return "LARGE";
        if (recentOrderCount >= 8)  return "MEDIUM";
        return "SMALL";
    }

    // ── Merchant credit status ─────────────────────────────────────────────

    /**
     * Compute credit status from payment on-time rate.
     *
     * Mirrors {@code OrderFeatureServiceImpl#computeMerchantCreditStatus}:
     * >= 90% on-time → GOOD, >= 70% → MODERATE, else → POOR, no payments → UNKNOWN.
     *
     * In synthetic data, a payment is "on-time" when {@code daysAfterInvoice <= 30}
     * (standard 30-day payment terms).
     */
    private String computeCreditStatus(List<SyntheticPayment> payments) {
        if (payments.isEmpty()) return "UNKNOWN";
        long onTimeCount = payments.stream()
                .filter(p -> !p.isDefault())
                .filter(p -> p.daysAfterInvoice() <= 30)
                .count();
        double onTimeRate = (double) onTimeCount / payments.size();
        if (onTimeRate >= 0.90) return "GOOD";
        if (onTimeRate >= 0.70) return "MODERATE";
        return "POOR";
    }

    // ── SKU price tier ─────────────────────────────────────────────────────

    /**
     * Derive price tier from the median unit price of the SKU across all order items.
     *
     * Mirrors {@code OrderFeatureServiceImpl#computePriceTier} thresholds:
     * < KES 500 → LOW, < KES 2000 → MEDIUM, >= KES 2000 → HIGH.
     */
    private String computePriceTier(UUID skuId, SyntheticDataBundle bundle) {
        List<BigDecimal> prices = bundle.getOrderItems().stream()
                .filter(item -> item.skuId().equals(skuId))
                .map(SyntheticOrderItem::unitPrice)
                .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        if (prices.isEmpty()) return "MEDIUM"; // same default as real service when price is mid-range
        BigDecimal avg = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);
        if (avg.compareTo(BigDecimal.valueOf(500)) < 0) return "LOW";
        if (avg.compareTo(BigDecimal.valueOf(2000)) < 0) return "MEDIUM";
        return "HIGH";
    }

    // ── Temporal helpers ───────────────────────────────────────────────────

    /**
     * Kenya public holidays — uses month/day pairs to work across training years.
     * Includes fixed holidays; Good Friday/Easter Monday vary by year so are excluded here.
     */
    private boolean isHoliday(LocalDate date) {
        int m = date.getMonthValue(), d = date.getDayOfMonth();
        return (m == 1 && d == 1)   // New Year's Day
            || (m == 5 && d == 1)   // Labour Day
            || (m == 6 && d == 1)   // Madaraka Day
            || (m == 10 && d == 10) // Huduma Day
            || (m == 10 && d == 20) // Mashujaa Day
            || (m == 12 && d == 12) // Jamhuri Day
            || (m == 12 && d == 25) // Christmas Day
            || (m == 12 && d == 26); // Boxing Day
    }

    /**
     * Payday week: 28th to end of month, or 1st–5th of month.
     * Mirrors {@code OrderFeatureServiceImpl#isPaydayWeek}.
     */
    private boolean isPaydayWeek(LocalDate date) {
        int day = date.getDayOfMonth();
        return day >= 28 || day <= 5;
    }

    /**
     * Ramadan approximation: March and/or April (valid for 2024–2027 range).
     * Mirrors the spirit of {@code OrderFeatureServiceImpl#isRamadan}.
     */
    private boolean isRamadan(LocalDate date) {
        int month = date.getMonthValue();
        return month == 3 || month == 4;
    }
}
