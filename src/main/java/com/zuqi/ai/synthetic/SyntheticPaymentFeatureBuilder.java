package com.zuqi.ai.synthetic;

import com.zuqi.ai.feature.MerchantPaymentTrendFeatures;
import com.zuqi.ai.feature.PaymentFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds {@link PaymentFeatures} and {@link MerchantPaymentTrendFeatures} from
 * in-memory synthetic data.
 *
 * <p>Computation logic mirrors {@link com.zuqi.ai.feature.PaymentFeatureServiceImpl}
 * exactly — only the data source differs.
 */
@Component
@Slf4j
public class SyntheticPaymentFeatureBuilder {

    /** Payments beyond this days threshold are classified as late. */
    private static final int LATE_THRESHOLD_DAYS = 30;

    // ── Per-payment features ───────────────────────────────────────────────

    /**
     * Compute per-payment anomaly detection features for a single synthetic payment.
     *
     * @param payment  the synthetic payment to analyse
     * @param bundle   the full in-memory dataset (needed for merchant context)
     * @return fully populated {@link PaymentFeatures}
     */
    public PaymentFeatures computePaymentFeatures(SyntheticPayment payment,
                                                   SyntheticDataBundle bundle) {
        UUID merchantId = payment.merchantRef();
        List<SyntheticPayment> allMerchantPayments = bundle.getPaymentsForMerchant(merchantId);

        // Look up the invoice (order) amount via the invoiceRef
        BigDecimal invoiceAmount = bundle.getOrders().stream()
                .filter(o -> o.syntheticId().equals(payment.invoiceRef()))
                .findFirst()
                .map(SyntheticOrder::totalAmount)
                .orElse(payment.amount());  // fallback: treat payment as full

        double merchantAvgDaysToPay = allMerchantPayments.stream()
                .mapToInt(SyntheticPayment::daysAfterInvoice)
                .average()
                .orElse(0.0);

        BigDecimal merchantAvgPayment = computeAvgPaymentAmount(allMerchantPayments);

        double amountVsInvoiceRatio = invoiceAmount.compareTo(BigDecimal.ZERO) == 0 ? 1.0 :
                payment.amount()
                        .divide(invoiceAmount, 4, RoundingMode.HALF_UP)
                        .doubleValue();

        double amountVsMerchantAvg = merchantAvgPayment.compareTo(BigDecimal.ZERO) == 0 ? 1.0 :
                payment.amount()
                        .divide(merchantAvgPayment, 4, RoundingMode.HALF_UP)
                        .doubleValue();

        log.debug("[SyntheticPaymentFB] payment={} merchant={} daysAfterInvoice={}",
                payment.syntheticId(), merchantId, payment.daysAfterInvoice());

        return PaymentFeatures.builder()
                .paymentId(payment.syntheticId())
                .merchantId(merchantId)
                .computedAt(payment.paymentDate())
                // Timing features
                .daysToPay((double) payment.daysAfterInvoice())
                .daysToPayVsMerchantAvg(payment.daysAfterInvoice() - merchantAvgDaysToPay)
                .gapSinceLastPaymentDays(computeGapSinceLastPayment(payment, allMerchantPayments))
                // Amount features
                .paymentAmount(payment.amount())
                .invoiceAmount(invoiceAmount)
                .amountVsInvoiceRatio(amountVsInvoiceRatio)
                .amountVsMerchantAvg(amountVsMerchantAvg)
                // Characteristics
                .paymentMethodEncoded(payment.paymentMethod())
                .hourOfDay(payment.paymentDate().getHour())
                .isPartial(payment.isPartial())
                .isLate(payment.daysAfterInvoice() > LATE_THRESHOLD_DAYS)
                // Context
                .merchantTotalPayments(allMerchantPayments.size())
                .merchantAvgPayment(merchantAvgPayment)
                .merchantAvgDaysToPay(merchantAvgDaysToPay)
                .build();
    }

    // ── Merchant-level trend features ──────────────────────────────────────

    /**
     * Compute 3-month merchant payment trend features for distress/default prediction.
     *
     * @param merchant  the synthetic merchant
     * @param bundle    the full in-memory dataset
     * @param asOfDate  reference date ("now")
     * @return fully populated {@link MerchantPaymentTrendFeatures}
     */
    public MerchantPaymentTrendFeatures computeMerchantTrendFeatures(SyntheticMerchant merchant,
                                                                      SyntheticDataBundle bundle,
                                                                      LocalDateTime asOfDate) {
        UUID mid = merchant.syntheticId();
        List<SyntheticPayment> allPayments = bundle.getPaymentsForMerchant(mid);
        List<SyntheticOrder>   allOrders   = bundle.getOrdersForMerchant(mid);

        LocalDateTime threeMonthsAgo = asOfDate.minusMonths(3);
        LocalDateTime sixMonthsAgo   = asOfDate.minusMonths(6);

        List<SyntheticPayment> payments3m = allPayments.stream()
                .filter(p -> p.paymentDate().isAfter(threeMonthsAgo))
                .collect(Collectors.toList());

        List<SyntheticPayment> prevPayments3m = allPayments.stream()
                .filter(p -> p.paymentDate().isAfter(sixMonthsAgo)
                        && !p.paymentDate().isAfter(threeMonthsAgo))
                .collect(Collectors.toList());

        List<SyntheticOrder> orders3m = allOrders.stream()
                .filter(o -> o.orderDate().isAfter(threeMonthsAgo))
                .collect(Collectors.toList());

        List<SyntheticOrder> prevOrders3m = allOrders.stream()
                .filter(o -> o.orderDate().isAfter(sixMonthsAgo)
                        && !o.orderDate().isAfter(threeMonthsAgo))
                .collect(Collectors.toList());

        // Payment timing trends
        double daysToPayTrend3m      = computeDaysToPayTrend(payments3m);
        double daysToPayStddev3m     = computeDaysToPayStddev(payments3m);
        double latePaymentRate3m     = computeLatePaymentRate(payments3m);
        double prevLatePaymentRate   = computeLatePaymentRate(prevPayments3m);
        double latePaymentRateTrend3m = latePaymentRate3m - prevLatePaymentRate;

        // Order frequency trends
        double weeksIn3m             = 13.0;  // ≈ 3 months in weeks
        double orderFrequency3m      = orders3m.size() / Math.max(1.0, weeksIn3m);
        double prevOrderFrequency    = prevOrders3m.size() / Math.max(1.0, weeksIn3m);
        double orderFrequencyTrend3m = prevOrderFrequency == 0.0 ? 0.0 :
                ((orderFrequency3m - prevOrderFrequency) / prevOrderFrequency) * 100.0;

        // Partial payment trends
        double partialPaymentFreq3m     = computePartialRate(payments3m);
        double prevPartialPaymentFreq   = computePartialRate(prevPayments3m);
        double partialPaymentFreqTrend3m = partialPaymentFreq3m - prevPartialPaymentFreq;

        // Order value trends
        double avgOrderValue3m    = computeAvgOrderValue(orders3m);
        double prevAvgOrderValue  = computeAvgOrderValue(prevOrders3m);
        double avgOrderValueTrend3m = prevAvgOrderValue == 0.0 ? 0.0 :
                ((avgOrderValue3m - prevAvgOrderValue) / prevAvgOrderValue) * 100.0;
        double orderValueVolatility3m = computeOrderValueStddev(orders3m);

        // Credit utilization (simplified — no real balance tracking in synthetic data)
        double creditUtilization3m = 0.3;  // Placeholder — archetype drives actual utilization

        // Outstanding and overdue
        int daysOverdueMax = payments3m.stream()
                .filter(p -> p.daysAfterInvoice() > LATE_THRESHOLD_DAYS)
                .mapToInt(p -> p.daysAfterInvoice() - LATE_THRESHOLD_DAYS)
                .max()
                .orElse(0);

        double paymentToOrderRatio3m = computePaymentToOrderRatio(payments3m, orders3m);

        log.debug("[SyntheticPaymentFB] trend merchant={} payments3m={} orders3m={}",
                mid, payments3m.size(), orders3m.size());

        return MerchantPaymentTrendFeatures.builder()
                .merchantId(mid)
                .computedAt(asOfDate)
                .daysToPayTrend3m(daysToPayTrend3m)
                .daysToPayStddev3m(daysToPayStddev3m)
                .latePaymentRate3m(latePaymentRate3m)
                .latePaymentRateTrend3m(latePaymentRateTrend3m)
                .orderFrequency3m(orderFrequency3m)
                .orderFrequencyTrend3m(orderFrequencyTrend3m)
                .consecutiveMissedOrders(computeConsecutiveMissedWeeks(allOrders, asOfDate))
                .creditUtilization3m(creditUtilization3m)
                .creditUtilizationTrajectory(0.0)
                .peakUtilization3m(0.0)
                .hitCreditLimit3m(creditUtilization3m >= 0.95)
                .partialPaymentFreq3m(partialPaymentFreq3m)
                .partialPaymentFreqTrend3m(partialPaymentFreqTrend3m)
                .consecutivePartialPayments(computeConsecutivePartialPayments(allPayments))
                .avgOrderValue3m(avgOrderValue3m)
                .avgOrderValueTrend3m(avgOrderValueTrend3m)
                .orderValueVolatility3m(orderValueVolatility3m)
                .totalOutstanding(BigDecimal.ZERO)
                .outstandingTrend3m(0.0)
                .daysOverdueMax(daysOverdueMax)
                .paymentToOrderRatio3m(paymentToOrderRatio3m)
                .build();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private BigDecimal computeAvgPaymentAmount(List<SyntheticPayment> payments) {
        if (payments.isEmpty()) return BigDecimal.ZERO;
        BigDecimal total = payments.stream()
                .map(SyntheticPayment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(payments.size()), 2, RoundingMode.HALF_UP);
    }

    private int computeGapSinceLastPayment(SyntheticPayment current,
                                            List<SyntheticPayment> allPayments) {
        return allPayments.stream()
                .filter(p -> !p.syntheticId().equals(current.syntheticId())
                        && p.paymentDate().isBefore(current.paymentDate()))
                .map(SyntheticPayment::paymentDate)
                .max(LocalDateTime::compareTo)
                .map(prev -> (int) ChronoUnit.DAYS.between(prev, current.paymentDate()))
                .orElse(0);
    }

    private double computeDaysToPayTrend(List<SyntheticPayment> payments) {
        if (payments.size() < 2) return 0.0;
        List<SyntheticPayment> sorted = payments.stream()
                .sorted(Comparator.comparing(SyntheticPayment::paymentDate))
                .toList();
        double[] x = new double[sorted.size()];
        double[] y = new double[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            x[i] = i;
            y[i] = sorted.get(i).daysAfterInvoice();
        }
        return FeatureComputationUtils.computeLinearRegressionSlope(x, y);
    }

    private double computeDaysToPayStddev(List<SyntheticPayment> payments) {
        if (payments.size() < 2) return 0.0;
        double[] values = payments.stream()
                .mapToDouble(SyntheticPayment::daysAfterInvoice)
                .toArray();
        return FeatureComputationUtils.computeStandardDeviation(values);
    }

    private double computeLatePaymentRate(List<SyntheticPayment> payments) {
        if (payments.isEmpty()) return 0.0;
        long late = payments.stream()
                .filter(p -> p.daysAfterInvoice() > LATE_THRESHOLD_DAYS)
                .count();
        return (double) late / payments.size();
    }

    private double computePartialRate(List<SyntheticPayment> payments) {
        if (payments.isEmpty()) return 0.0;
        long partial = payments.stream().filter(SyntheticPayment::isPartial).count();
        return (double) partial / payments.size();
    }

    private int computeConsecutiveMissedWeeks(List<SyntheticOrder> allOrders,
                                               LocalDateTime asOfDate) {
        int weeks = 0;
        for (int w = 0; w < 12; w++) {
            LocalDateTime weekStart = asOfDate.minusWeeks(w + 1);
            LocalDateTime weekEnd   = asOfDate.minusWeeks(w);
            boolean hasOrder = allOrders.stream().anyMatch(o ->
                    !o.orderDate().isBefore(weekStart) && o.orderDate().isBefore(weekEnd));
            if (!hasOrder) weeks++;
            else break;
        }
        return weeks;
    }

    private int computeConsecutivePartialPayments(List<SyntheticPayment> allPayments) {
        List<SyntheticPayment> sorted = allPayments.stream()
                .sorted(Comparator.comparing(SyntheticPayment::paymentDate).reversed())
                .toList();
        int streak = 0;
        for (SyntheticPayment p : sorted) {
            if (p.isPartial()) streak++;
            else break;
        }
        return streak;
    }

    private double computeAvgOrderValue(List<SyntheticOrder> orders) {
        if (orders.isEmpty()) return 0.0;
        return orders.stream()
                .mapToDouble(o -> o.totalAmount().doubleValue())
                .average()
                .orElse(0.0);
    }

    private double computeOrderValueStddev(List<SyntheticOrder> orders) {
        if (orders.size() < 2) return 0.0;
        double[] values = orders.stream()
                .mapToDouble(o -> o.totalAmount().doubleValue())
                .toArray();
        return FeatureComputationUtils.computeStandardDeviation(values);
    }

    private double computePaymentToOrderRatio(List<SyntheticPayment> payments,
                                               List<SyntheticOrder> orders) {
        BigDecimal totalPaid = payments.stream()
                .map(SyntheticPayment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOrdered = orders.stream()
                .map(SyntheticOrder::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalOrdered.compareTo(BigDecimal.ZERO) == 0) return 1.0;
        return totalPaid.divide(totalOrdered, 4, RoundingMode.HALF_UP).doubleValue();
    }
}
