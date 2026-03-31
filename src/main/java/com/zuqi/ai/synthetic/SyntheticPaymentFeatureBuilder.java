package com.zuqi.ai.synthetic;

import com.zuqi.ai.feature.FeatureComputationUtils;

import com.zuqi.ai.synthetic.dto.*;

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
     * <p>Mirrors {@link com.zuqi.ai.feature.PaymentFeatureServiceImpl#computeMerchantTrendFeatures}
     * field-for-field:
     * <ul>
     *   <li>latePaymentRateTrend3m  — ratio (current−prev)/prev, not a simple difference</li>
     *   <li>orderFrequencyTrend3m   — ratio (current−prev)/prev, not ×100 percentage</li>
     *   <li>avgOrderValueTrend3m    — ratio (current−prev)/prev, not ×100 percentage</li>
     *   <li>partialPaymentFreqTrend3m — ratio (current−prev)/prev, not a simple difference</li>
     *   <li>creditUtilization3m     — avg (outstanding/limit) across 3m orders</li>
     *   <li>creditUtilizationTrajectory — linear regression slope of per-order utilization</li>
     *   <li>peakUtilization3m       — max outstanding/limit across 3m orders</li>
     *   <li>totalOutstanding        — sum of unpaid balances across ALL orders</li>
     *   <li>outstandingTrend3m      — ratio of current vs pre-3m outstanding</li>
     *   <li>consecutiveMissedOrders — weeks since most recent order (not consecutive missed weeks)</li>
     * </ul>
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

        // Credit limit — use most recent evaluation, fall back to initial limit
        List<SyntheticCreditEvaluation> creditHistory = bundle.getCreditHistoryForMerchant(mid);
        BigDecimal creditLimit = creditHistory.stream()
                .max(Comparator.comparing(SyntheticCreditEvaluation::evaluationDate))
                .map(SyntheticCreditEvaluation::creditLimit)
                .orElse(merchant.initialCreditLimit());

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
        double daysToPayTrend3m  = computeDaysToPayTrend(payments3m);
        double daysToPayStddev3m = computeDaysToPayStddev(payments3m);
        double latePaymentRate3m   = computeLatePaymentRate(payments3m);
        double prevLatePaymentRate = computeLatePaymentRate(prevPayments3m);
        // Mirrors PaymentFeatureServiceImpl#computeLatePaymentRateTrend: ratio, not simple difference
        double latePaymentRateTrend3m = prevLatePaymentRate == 0.0
                ? (latePaymentRate3m > 0.0 ? 1.0 : 0.0)
                : (latePaymentRate3m - prevLatePaymentRate) / prevLatePaymentRate;

        // Order frequency trends
        // Mirrors PaymentFeatureServiceImpl#computeOrderFrequency (orders / weeks) and
        // computeOrderFrequencyTrend (ratio, not percentage)
        double weeksIn3m          = 13.0;  // ≈ 3 months in weeks
        double orderFrequency3m   = orders3m.size() / Math.max(1.0, weeksIn3m);
        double prevOrderFrequency = prevOrders3m.size() / Math.max(1.0, weeksIn3m);
        double orderFrequencyTrend3m = prevOrderFrequency == 0.0
                ? (orderFrequency3m > 0.0 ? 1.0 : 0.0)
                : (orderFrequency3m - prevOrderFrequency) / prevOrderFrequency;

        // Partial payment trends
        double partialPaymentFreq3m   = computePartialRate(payments3m);
        double prevPartialPaymentFreq = computePartialRate(prevPayments3m);
        // Mirrors PaymentFeatureServiceImpl#computePartialPaymentFreqTrend: ratio, not simple difference
        double partialPaymentFreqTrend3m = prevPartialPaymentFreq == 0.0
                ? (partialPaymentFreq3m > 0.0 ? 1.0 : 0.0)
                : (partialPaymentFreq3m - prevPartialPaymentFreq) / prevPartialPaymentFreq;

        // Order value trends
        double avgOrderValue3m   = computeAvgOrderValue(orders3m);
        double prevAvgOrderValue = computeAvgOrderValue(prevOrders3m);
        // Mirrors PaymentFeatureServiceImpl#computeAvgOrderValueTrend: ratio, not ×100 percentage
        double avgOrderValueTrend3m = prevAvgOrderValue == 0.0 ? 0.0 :
                (avgOrderValue3m - prevAvgOrderValue) / prevAvgOrderValue;
        double orderValueVolatility3m = computeOrderValueStddev(orders3m);

        // ── Credit utilization ─────────────────────────────────────────────
        // Mirrors PaymentFeatureServiceImpl#computeAvgCreditUtilization,
        // computeCreditUtilizationTrend, and computePeakCreditUtilization.
        // Outstanding per order = order.totalAmount - sum(payments for that order).
        double creditUtilization3m         = 0.0;
        double creditUtilizationTrajectory = 0.0;
        double peakUtilization3m           = 0.0;

        if (creditLimit.compareTo(BigDecimal.ZERO) != 0 && !orders3m.isEmpty()) {
            // Average utilization over the 3m window
            BigDecimal avgOutstanding = orders3m.stream()
                    .map(o -> computeOrderOutstanding(o, bundle))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(orders3m.size()), 4, RoundingMode.HALF_UP);
            creditUtilization3m = avgOutstanding.divide(creditLimit, 4, RoundingMode.HALF_UP).doubleValue();

            // Peak utilization
            BigDecimal maxOutstanding = orders3m.stream()
                    .map(o -> computeOrderOutstanding(o, bundle))
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            peakUtilization3m = maxOutstanding.divide(creditLimit, 4, RoundingMode.HALF_UP).doubleValue();

            // Utilization trajectory (linear regression slope over time-ordered orders)
            if (orders3m.size() >= 2) {
                List<SyntheticOrder> sortedOrders3m = orders3m.stream()
                        .sorted(Comparator.comparing(SyntheticOrder::orderDate))
                        .toList();
                double[] x = new double[sortedOrders3m.size()];
                double[] y = new double[sortedOrders3m.size()];
                for (int i = 0; i < sortedOrders3m.size(); i++) {
                    x[i] = i;
                    BigDecimal outstanding = computeOrderOutstanding(sortedOrders3m.get(i), bundle);
                    y[i] = outstanding.divide(creditLimit, 4, RoundingMode.HALF_UP).doubleValue();
                }
                creditUtilizationTrajectory = FeatureComputationUtils.computeLinearRegressionSlope(x, y);
            }
        }

        // ── Total outstanding (all orders) ────────────────────────────────
        // Mirrors PaymentFeatureServiceImpl#computeTotalOutstanding
        BigDecimal totalOutstanding = allOrders.stream()
                .map(o -> computeOrderOutstanding(o, bundle))
                .filter(amt -> amt.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Outstanding trend ─────────────────────────────────────────────
        // Mirrors PaymentFeatureServiceImpl#computeOutstandingTrend:
        // (currentOutstanding - previousOutstanding) / previousOutstanding
        // where "previous" = outstanding of orders created BEFORE threeMonthsAgo
        List<SyntheticOrder> oldOrders = allOrders.stream()
                .filter(o -> !o.orderDate().isAfter(threeMonthsAgo))
                .collect(Collectors.toList());
        BigDecimal previousOutstanding = oldOrders.stream()
                .map(o -> computeOrderOutstanding(o, bundle))
                .filter(amt -> amt.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double outstandingTrend3m;
        if (previousOutstanding.compareTo(BigDecimal.ZERO) == 0) {
            outstandingTrend3m = totalOutstanding.compareTo(BigDecimal.ZERO) > 0 ? 1.0 : 0.0;
        } else {
            outstandingTrend3m = totalOutstanding.subtract(previousOutstanding)
                    .divide(previousOutstanding, 4, RoundingMode.HALF_UP)
                    .doubleValue();
        }

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
                // Mirrors PaymentFeatureServiceImpl#computeConsecutiveMissedOrders:
                // weeks since most recent order, not consecutive missed weeks
                .consecutiveMissedOrders(computeWeeksSinceLastOrder(allOrders, asOfDate))
                .creditUtilization3m(creditUtilization3m)
                .creditUtilizationTrajectory(creditUtilizationTrajectory)
                .peakUtilization3m(peakUtilization3m)
                .hitCreditLimit3m(peakUtilization3m >= 0.95)
                .partialPaymentFreq3m(partialPaymentFreq3m)
                .partialPaymentFreqTrend3m(partialPaymentFreqTrend3m)
                .consecutivePartialPayments(computeConsecutivePartialPayments(allPayments))
                .avgOrderValue3m(avgOrderValue3m)
                .avgOrderValueTrend3m(avgOrderValueTrend3m)
                .orderValueVolatility3m(orderValueVolatility3m)
                .totalOutstanding(totalOutstanding)
                .outstandingTrend3m(outstandingTrend3m)
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

    /**
     * Weeks since the most recent order.
     *
     * Mirrors {@code PaymentFeatureServiceImpl#computeConsecutiveMissedOrders}:
     * finds the most-recent order and returns the number of full weeks between it and asOfDate.
     */
    private int computeWeeksSinceLastOrder(List<SyntheticOrder> allOrders,
                                            LocalDateTime asOfDate) {
        if (allOrders.isEmpty()) return 0;
        LocalDateTime mostRecent = allOrders.stream()
                .map(SyntheticOrder::orderDate)
                .max(LocalDateTime::compareTo)
                .orElse(asOfDate);
        return (int) Math.max(0, ChronoUnit.WEEKS.between(mostRecent, asOfDate));
    }

    /**
     * Outstanding balance for a single order: totalAmount minus sum of actual payments.
     *
     * Used to mirror {@code Order#getPaidAmount()} which is not available in synthetic records.
     */
    private BigDecimal computeOrderOutstanding(SyntheticOrder order, SyntheticDataBundle bundle) {
        BigDecimal paidAmount = bundle.getPaymentsForOrder(order.syntheticId()).stream()
                .map(SyntheticPayment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return order.totalAmount().subtract(paidAmount);
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
