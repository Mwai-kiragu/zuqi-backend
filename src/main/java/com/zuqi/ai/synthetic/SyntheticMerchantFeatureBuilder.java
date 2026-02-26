package com.zuqi.ai.synthetic;

import com.zuqi.ai.feature.MerchantFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds {@link MerchantFeatures} from in-memory synthetic data.
 *
 * <p>Computation logic mirrors {@link com.zuqi.ai.feature.MerchantFeatureServiceImpl}
 * exactly — only the data source differs (SyntheticDataBundle vs JPA repositories).
 * Null-returning computations in the real service are replaced with {@code 0} / {@code 0.0}
 * defaults so that downstream Tribuo feature builders never encounter NullPointerException.
 */
@Component
@Slf4j
public class SyntheticMerchantFeatureBuilder {

    /** Payments at or below this threshold are classified as on-time. */
    private static final int ON_TIME_DAYS_THRESHOLD = 30;

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Compute all merchant features for the given synthetic merchant.
     *
     * @param merchant  the synthetic merchant to compute features for
     * @param bundle    the full in-memory dataset (provides order/payment/credit lookups)
     * @param asOfDate  reference point in time ("now" for training purposes)
     * @return fully populated {@link MerchantFeatures}
     */
    public MerchantFeatures computeFeatures(SyntheticMerchant merchant,
                                             SyntheticDataBundle bundle,
                                             LocalDateTime asOfDate) {
        UUID mid = merchant.syntheticId();
        List<SyntheticOrder>            orders        = bundle.getOrdersForMerchant(mid);
        List<SyntheticPayment>          payments      = bundle.getPaymentsForMerchant(mid);
        List<SyntheticCreditEvaluation> creditHistory = bundle.getCreditHistoryForMerchant(mid);

        log.debug("[SyntheticMerchantFB] merchant={} orders={} payments={} creditEvals={}",
                mid, orders.size(), payments.size(), creditHistory.size());

        return MerchantFeatures.builder()
                .merchantId(mid)
                .computedAt(asOfDate)
                // ── Order features ──────────────────────────────────────
                .totalOrders(orders.size())
                .orderFrequencyPerWeek(computeOrderFrequencyPerWeek(orders, merchant, asOfDate))
                .avgOrderValue(computeAvgOrderValue(orders))
                .orderValueTrendSlope12w(computeOrderValueTrendSlope12w(orders, asOfDate))
                .orderConsistencyStddev(computeOrderConsistencyStddev(orders))
                .cancellationRate(computeCancellationRate(orders))
                .returnRate(0.0)
                .daysSinceLastOrder(computeDaysSinceLastOrder(orders, asOfDate))
                .uniqueSkusOrdered(computeUniqueSkusOrdered(orders, bundle))
                .topSkuConcentration(computeTopSkuConcentration(orders, bundle))
                // ── Payment features ────────────────────────────────────
                .totalPayments(payments.size())
                .onTimePaymentPct(computeOnTimePaymentPct(payments))
                .avgDaysToPay(computeAvgDaysToPay(payments))
                .worstDaysToPay(computeWorstDaysToPay(payments))
                .partialPaymentFrequency(computePartialPaymentFrequency(payments))
                .paymentMethodDistribution(computePaymentMethodDistribution(payments))
                .consecutiveOnTimeStreak(computeConsecutiveOnTimeStreak(payments))
                .totalOverdueAmount(BigDecimal.ZERO)
                // ── Credit features ─────────────────────────────────────
                .currentCreditLimit(computeCurrentCreditLimit(merchant, creditHistory))
                .currentUtilizationRatio(computeCurrentUtilizationRatio(merchant, creditHistory, orders))
                .peakUtilizationRatio(0.0)
                .utilizationTrendSlope(0.0)
                .limitIncreaseCount(computeLimitIncreaseCount(creditHistory))
                .daysSinceLastLimitChange(computeDaysSinceLastLimitChange(creditHistory, asOfDate))
                // ── Profile features ────────────────────────────────────
                .businessCategoryEncoded(merchant.businessCategory())
                .relationshipTenureDays((int) ChronoUnit.DAYS.between(
                        merchant.registrationDate(), asOfDate.toLocalDate()))
                .verificationStatus("UNVERIFIED")
                .geographicCluster(merchant.county())
                .build();
    }

    // ── Order computations ─────────────────────────────────────────────────

    private double computeOrderFrequencyPerWeek(List<SyntheticOrder> orders,
                                                 SyntheticMerchant merchant,
                                                 LocalDateTime asOfDate) {
        if (orders.isEmpty()) return 0.0;
        LocalDateTime first = orders.stream()
                .map(SyntheticOrder::orderDate)
                .min(LocalDateTime::compareTo)
                .orElse(asOfDate);
        long days = ChronoUnit.DAYS.between(first, asOfDate);
        if (days == 0) return (double) orders.size();
        return orders.size() / (days / 7.0);
    }

    private BigDecimal computeAvgOrderValue(List<SyntheticOrder> orders) {
        if (orders.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = orders.stream()
                .map(SyntheticOrder::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
    }

    private Double computeOrderValueTrendSlope12w(List<SyntheticOrder> orders,
                                                   LocalDateTime asOfDate) {
        LocalDateTime twelveWeeksAgo = asOfDate.minusWeeks(12);
        List<SyntheticOrder> recent = orders.stream()
                .filter(o -> o.orderDate().isAfter(twelveWeeksAgo))
                .sorted(Comparator.comparing(SyntheticOrder::orderDate))
                .toList();
        if (recent.size() < 2) return 0.0;
        double[] x = new double[recent.size()];
        double[] y = new double[recent.size()];
        for (int i = 0; i < recent.size(); i++) {
            x[i] = i;
            y[i] = recent.get(i).totalAmount().doubleValue();
        }
        return FeatureComputationUtils.computeLinearRegressionSlope(x, y);
    }

    private Double computeOrderConsistencyStddev(List<SyntheticOrder> orders) {
        if (orders.size() < 2) return 0.0;
        double[] values = orders.stream()
                .mapToDouble(o -> o.totalAmount().doubleValue())
                .toArray();
        return FeatureComputationUtils.computeStandardDeviation(values);
    }

    private Double computeCancellationRate(List<SyntheticOrder> orders) {
        if (orders.isEmpty()) return 0.0;
        long cancelled = orders.stream().filter(o -> "CANCELLED".equals(o.status())).count();
        return (double) cancelled / orders.size();
    }

    private Integer computeDaysSinceLastOrder(List<SyntheticOrder> orders,
                                               LocalDateTime asOfDate) {
        return orders.stream()
                .map(SyntheticOrder::orderDate)
                .max(LocalDateTime::compareTo)
                .map(last -> (int) ChronoUnit.DAYS.between(last, asOfDate))
                .orElse(Integer.MAX_VALUE);
    }

    private Integer computeUniqueSkusOrdered(List<SyntheticOrder> orders,
                                              SyntheticDataBundle bundle) {
        return (int) orders.stream()
                .flatMap(o -> bundle.getItemsForOrder(o.syntheticId()).stream())
                .map(SyntheticOrderItem::skuId)
                .distinct()
                .count();
    }

    private Double computeTopSkuConcentration(List<SyntheticOrder> orders,
                                               SyntheticDataBundle bundle) {
        if (orders.isEmpty()) return 0.0;
        Map<UUID, Long> skuCounts = orders.stream()
                .flatMap(o -> bundle.getItemsForOrder(o.syntheticId()).stream())
                .collect(Collectors.groupingBy(SyntheticOrderItem::skuId, Collectors.counting()));
        long maxCount   = skuCounts.values().stream().max(Long::compareTo).orElse(0L);
        long totalItems = skuCounts.values().stream().mapToLong(Long::longValue).sum();
        return totalItems > 0 ? (double) maxCount / totalItems : 0.0;
    }

    // ── Payment computations ───────────────────────────────────────────────

    /** Returns {@code 1.0} (fully on-time) when there are no payments. */
    private Double computeOnTimePaymentPct(List<SyntheticPayment> payments) {
        if (payments.isEmpty()) return 1.0;
        long onTime = payments.stream()
                .filter(p -> p.daysAfterInvoice() <= ON_TIME_DAYS_THRESHOLD)
                .count();
        return (double) onTime / payments.size();
    }

    /** Returns {@code 0.0} when there are no payments. */
    private Double computeAvgDaysToPay(List<SyntheticPayment> payments) {
        if (payments.isEmpty()) return 0.0;
        return payments.stream()
                .mapToInt(SyntheticPayment::daysAfterInvoice)
                .average()
                .orElse(0.0);
    }

    /** Returns {@code 0} when there are no payments. */
    private Integer computeWorstDaysToPay(List<SyntheticPayment> payments) {
        return payments.stream()
                .mapToInt(SyntheticPayment::daysAfterInvoice)
                .max()
                .orElse(0);
    }

    private Double computePartialPaymentFrequency(List<SyntheticPayment> payments) {
        if (payments.isEmpty()) return 0.0;
        long partial = payments.stream().filter(SyntheticPayment::isPartial).count();
        return (double) partial / payments.size();
    }

    private Map<String, Integer> computePaymentMethodDistribution(List<SyntheticPayment> payments) {
        return payments.stream()
                .collect(Collectors.groupingBy(
                        SyntheticPayment::paymentMethod,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private Integer computeConsecutiveOnTimeStreak(List<SyntheticPayment> payments) {
        List<SyntheticPayment> sorted = payments.stream()
                .sorted(Comparator.comparing(SyntheticPayment::paymentDate).reversed())
                .toList();
        int streak = 0;
        for (SyntheticPayment p : sorted) {
            if (p.daysAfterInvoice() <= ON_TIME_DAYS_THRESHOLD) streak++;
            else break;
        }
        return streak;
    }

    // ── Credit computations ────────────────────────────────────────────────

    private BigDecimal computeCurrentCreditLimit(SyntheticMerchant merchant,
                                                  List<SyntheticCreditEvaluation> creditHistory) {
        return creditHistory.stream()
                .max(Comparator.comparing(SyntheticCreditEvaluation::evaluationDate))
                .map(SyntheticCreditEvaluation::creditLimit)
                .orElse(merchant.initialCreditLimit());
    }

    private Double computeCurrentUtilizationRatio(SyntheticMerchant merchant,
                                                    List<SyntheticCreditEvaluation> creditHistory,
                                                    List<SyntheticOrder> orders) {
        BigDecimal limit = computeCurrentCreditLimit(merchant, creditHistory);
        if (limit.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        // Approximate outstanding balance as 30% of delivered orders total
        BigDecimal outstanding = orders.stream()
                .filter(o -> "DELIVERED".equals(o.status()))
                .map(SyntheticOrder::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(0.3));
        return outstanding.divide(limit, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private Integer computeLimitIncreaseCount(List<SyntheticCreditEvaluation> creditHistory) {
        if (creditHistory.size() < 2) return 0;
        List<SyntheticCreditEvaluation> sorted = creditHistory.stream()
                .sorted(Comparator.comparing(SyntheticCreditEvaluation::evaluationDate))
                .toList();
        int increases = 0;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).creditLimit().compareTo(sorted.get(i - 1).creditLimit()) > 0) {
                increases++;
            }
        }
        return increases;
    }

    /** Returns {@code 0} when there is no credit history. */
    private Integer computeDaysSinceLastLimitChange(List<SyntheticCreditEvaluation> creditHistory,
                                                      LocalDateTime asOfDate) {
        return creditHistory.stream()
                .map(SyntheticCreditEvaluation::evaluationDate)
                .max(Comparator.naturalOrder())
                .map(last -> (int) ChronoUnit.DAYS.between(last, asOfDate.toLocalDate()))
                .orElse(0);
    }
}
