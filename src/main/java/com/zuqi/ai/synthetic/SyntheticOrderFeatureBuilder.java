package com.zuqi.ai.synthetic;

import com.zuqi.ai.synthetic.dto.*;

import com.zuqi.ai.feature.DemandFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    // Representative Kenya public holidays (month-day pairs, year-independent)
    private static final int[][] KENYA_HOLIDAY_MONTH_DAY = {
            {1, 1},   // New Year's Day
            {4, 19},  // Good Friday (approximate)
            {5, 1},   // Labour Day
            {6, 1},   // Madaraka Day
            {10, 20}, // Mashujaa Day
            {12, 12}, // Jamhuri Day
            {12, 25}  // Christmas Day
    };

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

        // Lag features
        BigDecimal qty1w = qtyInWeek(skuId, bundle, asOfDate, 1);
        BigDecimal qty2w = qtyInWeek(skuId, bundle, asOfDate, 2);
        BigDecimal qty3w = qtyInWeek(skuId, bundle, asOfDate, 3);
        BigDecimal qty4w = qtyInWeek(skuId, bundle, asOfDate, 4);

        BigDecimal rolling4w = List.of(qty1w, qty2w, qty3w, qty4w).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(4), 3, RoundingMode.HALF_UP);

        BigDecimal rolling12w = computeRollingAvg(skuId, skuOrders, bundle, asOfDate, 12);
        String trendDirection = computeTrendDirection(skuId, skuOrders, bundle, asOfDate);

        LocalDate asOf = asOfDate.toLocalDate();

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
                .isChristmasSeason(asOf.getMonthValue() >= 11)
                // Merchant context
                .merchantCategory(merchant.businessCategory())
                .merchantSizeTier(computeMerchantSizeTier(allOrders))
                .merchantCreditStatus(computeCreditStatus(bundle.getCreditHistoryForMerchant(mid)))
                .merchantTenureDays((int) ChronoUnit.DAYS.between(merchant.registrationDate(), asOf))
                // SKU context (simplified — no real product catalogue in synthetic data)
                .productCategory("GENERAL")
                .priceTier("MEDIUM")
                .isPromotional(false)
                .typicalShelfLifeDays(90)
                .build();
    }

    // ── Lag / rolling helpers ──────────────────────────────────────────────

    /**
     * Sum the quantity of {@code skuId} ordered during the week that ended
     * {@code weeksAgo} weeks before {@code asOfDate}.
     */
    private BigDecimal qtyInWeek(UUID skuId, SyntheticDataBundle bundle,
                                  LocalDateTime asOfDate, int weeksAgo) {
        LocalDateTime weekEnd   = asOfDate.minusWeeks(weeksAgo - 1L);
        LocalDateTime weekStart = asOfDate.minusWeeks(weeksAgo);
        return bundle.getOrders().stream()
                .filter(o -> !o.orderDate().isBefore(weekStart) && o.orderDate().isBefore(weekEnd))
                .flatMap(o -> bundle.getItemsForOrder(o.syntheticId()).stream())
                .filter(item -> item.skuId().equals(skuId))
                .map(SyntheticOrderItem::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Compute the average weekly quantity over the {@code weeks}-week window ending at
     * {@code asOfDate}, using pre-filtered {@code skuOrders}.
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
        return totalQty.divide(BigDecimal.valueOf(weeks), 3, RoundingMode.HALF_UP);
    }

    private String computeTrendDirection(UUID skuId,
                                          List<SyntheticOrder> skuOrders,
                                          SyntheticDataBundle bundle,
                                          LocalDateTime asOfDate) {
        BigDecimal recent4w = computeRollingAvg(skuId, skuOrders, bundle, asOfDate, 4);
        BigDecimal prev4w = skuOrders.stream()
                .filter(o -> o.orderDate().isAfter(asOfDate.minusWeeks(8))
                        && o.orderDate().isBefore(asOfDate.minusWeeks(4)))
                .flatMap(o -> bundle.getItemsForOrder(o.syntheticId()).stream())
                .filter(item -> item.skuId().equals(skuId))
                .map(SyntheticOrderItem::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(4), 3, RoundingMode.HALF_UP);

        if (prev4w.compareTo(BigDecimal.ZERO) == 0) return "STABLE";
        double pctChange = recent4w.subtract(prev4w)
                .divide(prev4w, 4, RoundingMode.HALF_UP)
                .doubleValue();
        if (pctChange >  0.10) return "INCREASING";
        if (pctChange < -0.10) return "DECREASING";
        return "STABLE";
    }

    // ── Temporal helpers ───────────────────────────────────────────────────

    private boolean isHoliday(LocalDate date) {
        for (int[] md : KENYA_HOLIDAY_MONTH_DAY) {
            if (md[0] == date.getMonthValue() && md[1] == date.getDayOfMonth()) return true;
        }
        return false;
    }

    private boolean isPaydayWeek(LocalDate date) {
        int day    = date.getDayOfMonth();
        int maxDay = date.lengthOfMonth();
        // Payday week: 28th–end of month or 1st–5th of month
        return (day >= 28 && day <= maxDay) || day <= 5;
    }

    private boolean isRamadan(LocalDate date) {
        // Very rough approximation: Ramadan falls in March–April for 2024–2026 timeframe
        int month = date.getMonthValue();
        return month == 3 || month == 4;
    }

    // ── Merchant context helpers ───────────────────────────────────────────

    private String computeMerchantSizeTier(List<SyntheticOrder> orders) {
        if (orders.isEmpty()) return "SMALL";
        double avg = orders.stream()
                .mapToDouble(o -> o.totalAmount().doubleValue())
                .average()
                .orElse(0.0);
        if (avg >= 50_000) return "LARGE";
        if (avg >= 15_000) return "MEDIUM";
        return "SMALL";
    }

    private String computeCreditStatus(List<SyntheticCreditEvaluation> creditHistory) {
        if (creditHistory.isEmpty()) return "MODERATE";
        String grade = creditHistory.stream()
                .max(Comparator.comparing(SyntheticCreditEvaluation::evaluationDate))
                .map(SyntheticCreditEvaluation::grade)
                .orElse("C");
        return switch (grade) {
            case "A", "B" -> "GOOD";
            case "C"      -> "MODERATE";
            default       -> "POOR";
        };
    }
}
