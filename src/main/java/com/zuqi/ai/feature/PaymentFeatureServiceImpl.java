package com.zuqi.ai.feature;

import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.payment.Payment;
import com.zuqi.repository.MerchantRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFeatureServiceImpl implements PaymentFeatureService {

    private final PaymentRepository paymentRepository;
    private final MerchantRepository merchantRepository;
    private final OrderRepository orderRepository;

    @Override
    @Cacheable(value = "paymentFeatures", key = "#paymentId")
    @Transactional(readOnly = true)
    public PaymentFeatures computePaymentFeatures(UUID paymentId) {
        return computePaymentFeatures(paymentId, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentFeatures computePaymentFeatures(UUID paymentId, LocalDateTime asOfDate) {
        log.debug("Computing payment features for {} as of {}", paymentId, asOfDate);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        Merchant merchant = payment.getMerchant();
        if (merchant == null) {
            throw new IllegalArgumentException("Payment has no associated merchant: " + paymentId);
        }

        // Get merchant's historical payments for context
        List<Payment> merchantPayments = paymentRepository.findByMerchantIdAndCreatedAtBefore(
                merchant.getId(), asOfDate);

        // Compute merchant-level statistics for comparison
        MerchantPaymentStats merchantStats = computeMerchantStats(merchantPayments);

        return PaymentFeatures.builder()
                .paymentId(payment.getId())
                .merchantId(merchant.getId())
                .computedAt(asOfDate)
                // Payment timing features
                .daysToPay(computeDaysToPay(payment))
                .daysToPayVsMerchantAvg(computeDaysToPayVsAvg(payment, merchantStats))
                .gapSinceLastPaymentDays(computeGapSinceLastPayment(payment, merchantPayments, asOfDate))
                // Payment amount features
                .paymentAmount(payment.getAmount())
                .invoiceAmount(getInvoiceAmount(payment))
                .amountVsInvoiceRatio(computeAmountVsInvoiceRatio(payment))
                .amountVsMerchantAvg(computeAmountVsMerchantAvg(payment, merchantStats))
                // Payment characteristics
                .paymentMethodEncoded(getPaymentMethodName(payment))
                .hourOfDay(payment.getCreatedAt().getHour())
                .isPartial(isPartialPayment(payment))
                .isLate(isLatePayment(payment))
                // Context features
                .merchantTotalPayments(merchantStats.totalPayments())
                .merchantAvgPayment(merchantStats.avgPaymentAmount())
                .merchantAvgDaysToPay(merchantStats.avgDaysToPay())
                .build();
    }

    @Override
    @Cacheable(value = "merchantPaymentTrends", key = "#merchantId")
    @Transactional(readOnly = true)
    public MerchantPaymentTrendFeatures computeMerchantTrendFeatures(UUID merchantId) {
        return computeMerchantTrendFeatures(merchantId, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantPaymentTrendFeatures computeMerchantTrendFeatures(UUID merchantId, LocalDateTime asOfDate) {
        log.debug("Computing merchant payment trend features for {} as of {}", merchantId, asOfDate);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        // Get payments and orders for trend analysis
        List<Payment> allPayments = paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate);
        List<Order> allOrders = orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate);

        // Split into time windows
        LocalDateTime threeMonthsAgo = asOfDate.minusMonths(3);
        LocalDateTime sixMonthsAgo = asOfDate.minusMonths(6);

        List<Payment> last3Months = filterByDateRange(allPayments, threeMonthsAgo, asOfDate);
        List<Payment> previous3Months = filterByDateRange(allPayments, sixMonthsAgo, threeMonthsAgo);

        List<Order> last3MonthsOrders = filterOrdersByDateRange(allOrders, threeMonthsAgo, asOfDate);
        List<Order> previous3MonthsOrders = filterOrdersByDateRange(allOrders, sixMonthsAgo, threeMonthsAgo);

        return MerchantPaymentTrendFeatures.builder()
                .merchantId(merchantId)
                .computedAt(asOfDate)
                // Payment timing trends
                .daysToPayTrend3m(computeDaysToPayTrend(last3Months))
                .daysToPayStddev3m(computeDaysToPayStddev(last3Months))
                .latePaymentRate3m(computeLatePaymentRate(last3Months))
                .latePaymentRateTrend3m(computeLatePaymentRateTrend(last3Months, previous3Months))
                // Order frequency trends
                .orderFrequency3m(computeOrderFrequency(last3MonthsOrders, 3))
                .orderFrequencyTrend3m(computeOrderFrequencyTrend(last3MonthsOrders, previous3MonthsOrders))
                .consecutiveMissedOrders(computeConsecutiveMissedOrders(allOrders, asOfDate))
                // Credit utilization trends
                .creditUtilization3m(computeAvgCreditUtilization(last3MonthsOrders, merchant))
                .creditUtilizationTrajectory(computeCreditUtilizationTrend(last3MonthsOrders, merchant))
                .peakUtilization3m(computePeakCreditUtilization(last3MonthsOrders, merchant))
                .hitCreditLimit3m(checkHitCreditLimit(last3MonthsOrders, merchant))
                // Partial payment trends
                .partialPaymentFreq3m(computePartialPaymentFrequency(last3Months))
                .partialPaymentFreqTrend3m(computePartialPaymentFreqTrend(last3Months, previous3Months))
                .consecutivePartialPayments(computeConsecutivePartialPayments(allPayments, asOfDate))
                // Order value trends
                .avgOrderValue3m(computeAvgOrderValue(last3MonthsOrders))
                .avgOrderValueTrend3m(computeAvgOrderValueTrend(last3MonthsOrders, previous3MonthsOrders))
                .orderValueVolatility3m(computeOrderValueVolatility(last3MonthsOrders))
                // Overall financial health
                .totalOutstanding(computeTotalOutstanding(allOrders))
                .outstandingTrend3m(computeOutstandingTrend(allOrders, threeMonthsAgo))
                .daysOverdueMax(computeMaxDaysOverdue(allOrders, asOfDate))
                .paymentToOrderRatio3m(computePaymentToOrderRatio(last3Months, last3MonthsOrders))
                .build();
    }

    @Override
    @CacheEvict(value = "paymentFeatures", key = "#paymentId")
    public void evictPaymentCache(UUID paymentId) {
        log.debug("Evicted payment features cache for: {}", paymentId);
    }

    @Override
    @CacheEvict(value = "merchantPaymentTrends", key = "#merchantId")
    public void evictMerchantTrendCache(UUID merchantId) {
        log.debug("Evicted merchant payment trend cache for: {}", merchantId);
    }

    // ===========================
    // Per-Payment Feature Computations
    // ===========================

    private Double computeDaysToPay(Payment payment) {
        if (payment.getOrder() == null || payment.getOrder().getCreatedAt() == null) {
            return null;
        }
        return (double) ChronoUnit.DAYS.between(
                payment.getOrder().getCreatedAt().toLocalDate(),
                payment.getCreatedAt().toLocalDate()
        );
    }

    private Double computeDaysToPayVsAvg(Payment payment, MerchantPaymentStats stats) {
        Double daysToPay = computeDaysToPay(payment);
        if (daysToPay == null || stats.avgDaysToPay() == null) {
            return null;
        }
        return daysToPay - stats.avgDaysToPay();
    }

    private Integer computeGapSinceLastPayment(Payment payment, List<Payment> merchantPayments, LocalDateTime asOfDate) {
        List<Payment> priorPayments = merchantPayments.stream()
                .filter(p -> p.getCreatedAt().isBefore(payment.getCreatedAt()))
                .sorted(Comparator.comparing(Payment::getCreatedAt).reversed())
                .toList();

        if (priorPayments.isEmpty()) {
            return null;
        }

        Payment lastPayment = priorPayments.get(0);
        return (int) ChronoUnit.DAYS.between(lastPayment.getCreatedAt().toLocalDate(), payment.getCreatedAt().toLocalDate());
    }

    private BigDecimal getInvoiceAmount(Payment payment) {
        if (payment.getOrder() == null) {
            return BigDecimal.ZERO;
        }
        return payment.getOrder().getTotalAmount();
    }

    private Double computeAmountVsInvoiceRatio(Payment payment) {
        BigDecimal invoiceAmount = getInvoiceAmount(payment);
        if (invoiceAmount.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return payment.getAmount().divide(invoiceAmount, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private Double computeAmountVsMerchantAvg(Payment payment, MerchantPaymentStats stats) {
        if (stats.avgPaymentAmount() == null || stats.avgPaymentAmount().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return payment.getAmount().divide(stats.avgPaymentAmount(), 4, RoundingMode.HALF_UP).doubleValue();
    }

    private String getPaymentMethodName(Payment payment) {
        if (payment.getPaymentMethod() == null) {
            return "UNKNOWN";
        }
        return payment.getPaymentMethod().getName();
    }

    private Boolean isPartialPayment(Payment payment) {
        BigDecimal invoiceAmount = getInvoiceAmount(payment);
        if (invoiceAmount.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        return payment.getAmount().compareTo(invoiceAmount) < 0;
    }

    private Boolean isLatePayment(Payment payment) {
        if (payment.getOrder() == null || payment.getOrder().getPaymentDueDate() == null) {
            return false;
        }
        return payment.getCreatedAt().toLocalDate().isAfter(payment.getOrder().getPaymentDueDate());
    }

    // ===========================
    // Merchant Trend Feature Computations
    // ===========================

    private Double computeDaysToPayTrend(List<Payment> payments) {
        if (payments.size() < 2) {
            return 0.0;
        }

        List<Payment> sorted = payments.stream()
                .filter(p -> computeDaysToPay(p) != null)
                .sorted(Comparator.comparing(Payment::getCreatedAt))
                .toList();

        if (sorted.size() < 2) {
            return 0.0;
        }

        double[] x = new double[sorted.size()];
        double[] y = new double[sorted.size()];

        for (int i = 0; i < sorted.size(); i++) {
            x[i] = i;
            y[i] = computeDaysToPay(sorted.get(i));
        }

        return computeLinearRegressionSlope(x, y);
    }

    private Double computeDaysToPayStddev(List<Payment> payments) {
        List<Double> daysToPay = payments.stream()
                .map(this::computeDaysToPay)
                .filter(Objects::nonNull)
                .toList();

        if (daysToPay.size() < 2) {
            return 0.0;
        }

        double mean = daysToPay.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = daysToPay.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    private Double computeLatePaymentRate(List<Payment> payments) {
        if (payments.isEmpty()) {
            return 0.0;
        }

        long lateCount = payments.stream()
                .filter(this::isLatePayment)
                .count();

        return (double) lateCount / payments.size();
    }

    private Double computeLatePaymentRateTrend(List<Payment> current, List<Payment> previous) {
        Double currentRate = computeLatePaymentRate(current);
        Double previousRate = computeLatePaymentRate(previous);

        if (previousRate == 0.0) {
            return currentRate > 0.0 ? 1.0 : 0.0;
        }

        return (currentRate - previousRate) / previousRate;
    }

    private Double computeOrderFrequency(List<Order> orders, int months) {
        if (orders.isEmpty()) {
            return 0.0;
        }

        double weeks = months * 4.33; // Average weeks per month
        return orders.size() / weeks;
    }

    private Double computeOrderFrequencyTrend(List<Order> current, List<Order> previous) {
        Double currentFreq = computeOrderFrequency(current, 3);
        Double previousFreq = computeOrderFrequency(previous, 3);

        if (previousFreq == 0.0) {
            return currentFreq > 0.0 ? 1.0 : 0.0;
        }

        return (currentFreq - previousFreq) / previousFreq;
    }

    private Integer computeConsecutiveMissedOrders(List<Order> allOrders, LocalDateTime asOfDate) {
        if (allOrders.isEmpty()) {
            return 0;
        }

        // Sort by creation date descending
        List<Order> sorted = allOrders.stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .toList();

        // Find most recent order
        Order mostRecent = sorted.get(0);
        long weeksSinceLastOrder = ChronoUnit.WEEKS.between(mostRecent.getCreatedAt(), asOfDate);

        return (int) Math.max(0, weeksSinceLastOrder);
    }

    private Double computeAvgCreditUtilization(List<Order> orders, Merchant merchant) {
        if (merchant.getCreditLimit() == null || merchant.getCreditLimit().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        if (orders.isEmpty()) {
            return 0.0;
        }

        // Calculate average outstanding balance over the period
        BigDecimal avgOutstanding = orders.stream()
                .map(o -> o.getTotalAmount().subtract(o.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(orders.size()), 4, RoundingMode.HALF_UP);

        return avgOutstanding.divide(merchant.getCreditLimit(), 4, RoundingMode.HALF_UP).doubleValue();
    }

    private Double computeCreditUtilizationTrend(List<Order> orders, Merchant merchant) {
        if (merchant.getCreditLimit() == null || merchant.getCreditLimit().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        if (orders.size() < 2) {
            return 0.0;
        }

        List<Order> sorted = orders.stream()
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .toList();

        double[] x = new double[sorted.size()];
        double[] y = new double[sorted.size()];

        for (int i = 0; i < sorted.size(); i++) {
            x[i] = i;
            BigDecimal outstanding = sorted.get(i).getTotalAmount().subtract(sorted.get(i).getPaidAmount());
            y[i] = outstanding.divide(merchant.getCreditLimit(), 4, RoundingMode.HALF_UP).doubleValue();
        }

        return computeLinearRegressionSlope(x, y);
    }

    private Double computePeakCreditUtilization(List<Order> orders, Merchant merchant) {
        if (merchant.getCreditLimit() == null || merchant.getCreditLimit().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        if (orders.isEmpty()) {
            return 0.0;
        }

        BigDecimal maxOutstanding = orders.stream()
                .map(o -> o.getTotalAmount().subtract(o.getPaidAmount()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return maxOutstanding.divide(merchant.getCreditLimit(), 4, RoundingMode.HALF_UP).doubleValue();
    }

    private Boolean checkHitCreditLimit(List<Order> orders, Merchant merchant) {
        Double peak = computePeakCreditUtilization(orders, merchant);
        return peak != null && peak >= 0.95;
    }

    private Double computePartialPaymentFrequency(List<Payment> payments) {
        if (payments.isEmpty()) {
            return 0.0;
        }

        long partialCount = payments.stream()
                .filter(this::isPartialPayment)
                .count();

        return (double) partialCount / payments.size();
    }

    private Double computePartialPaymentFreqTrend(List<Payment> current, List<Payment> previous) {
        Double currentFreq = computePartialPaymentFrequency(current);
        Double previousFreq = computePartialPaymentFrequency(previous);

        if (previousFreq == 0.0) {
            return currentFreq > 0.0 ? 1.0 : 0.0;
        }

        return (currentFreq - previousFreq) / previousFreq;
    }

    private Integer computeConsecutivePartialPayments(List<Payment> allPayments, LocalDateTime asOfDate) {
        List<Payment> sorted = allPayments.stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt).reversed())
                .toList();

        int streak = 0;
        for (Payment payment : sorted) {
            if (isPartialPayment(payment)) {
                streak++;
            } else {
                break;
            }
        }

        return streak;
    }

    private Double computeAvgOrderValue(List<Order> orders) {
        if (orders.isEmpty()) {
            return null;
        }

        BigDecimal total = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP).doubleValue();
    }

    private Double computeAvgOrderValueTrend(List<Order> current, List<Order> previous) {
        Double currentAvg = computeAvgOrderValue(current);
        Double previousAvg = computeAvgOrderValue(previous);

        if (currentAvg == null || previousAvg == null || previousAvg == 0.0) {
            return null;
        }

        return (currentAvg - previousAvg) / previousAvg;
    }

    private Double computeOrderValueVolatility(List<Order> orders) {
        if (orders.size() < 2) {
            return 0.0;
        }

        double[] values = orders.stream()
                .mapToDouble(o -> o.getTotalAmount().doubleValue())
                .toArray();

        double mean = Arrays.stream(values).average().orElse(0.0);
        double variance = Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    private BigDecimal computeTotalOutstanding(List<Order> orders) {
        return orders.stream()
                .map(o -> o.getTotalAmount().subtract(o.getPaidAmount()))
                .filter(amt -> amt.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Double computeOutstandingTrend(List<Order> allOrders, LocalDateTime threeMonthsAgo) {
        BigDecimal currentOutstanding = computeTotalOutstanding(allOrders);

        List<Order> oldOrders = allOrders.stream()
                .filter(o -> o.getCreatedAt().isBefore(threeMonthsAgo))
                .toList();
        BigDecimal previousOutstanding = computeTotalOutstanding(oldOrders);

        if (previousOutstanding.compareTo(BigDecimal.ZERO) == 0) {
            return currentOutstanding.compareTo(BigDecimal.ZERO) > 0 ? 1.0 : 0.0;
        }

        return currentOutstanding.subtract(previousOutstanding)
                .divide(previousOutstanding, 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Integer computeMaxDaysOverdue(List<Order> orders, LocalDateTime asOfDate) {
        return orders.stream()
                .filter(o -> o.getPaymentDueDate() != null)
                .filter(o -> o.getPaidAmount().compareTo(o.getTotalAmount()) < 0)
                .filter(o -> asOfDate.toLocalDate().isAfter(o.getPaymentDueDate()))
                .map(o -> (int) ChronoUnit.DAYS.between(o.getPaymentDueDate(), asOfDate.toLocalDate()))
                .max(Integer::compareTo)
                .orElse(0);
    }

    private Double computePaymentToOrderRatio(List<Payment> payments, List<Order> orders) {
        if (orders.isEmpty()) {
            return null;
        }

        BigDecimal totalPayments = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOrders = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalOrders.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return totalPayments.divide(totalOrders, 4, RoundingMode.HALF_UP).doubleValue();
    }

    // ===========================
    // Helper Methods
    // ===========================

    private MerchantPaymentStats computeMerchantStats(List<Payment> payments) {
        if (payments.isEmpty()) {
            return new MerchantPaymentStats(0, BigDecimal.ZERO, null);
        }

        BigDecimal totalAmount = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgAmount = totalAmount.divide(BigDecimal.valueOf(payments.size()), 2, RoundingMode.HALF_UP);

        List<Double> daysToPay = payments.stream()
                .map(this::computeDaysToPay)
                .filter(Objects::nonNull)
                .toList();

        Double avgDays = daysToPay.isEmpty() ? null : daysToPay.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return new MerchantPaymentStats(payments.size(), avgAmount, avgDays);
    }

    private List<Payment> filterByDateRange(List<Payment> payments, LocalDateTime start, LocalDateTime end) {
        return payments.stream()
                .filter(p -> !p.getCreatedAt().isBefore(start) && p.getCreatedAt().isBefore(end))
                .toList();
    }

    private List<Order> filterOrdersByDateRange(List<Order> orders, LocalDateTime start, LocalDateTime end) {
        return orders.stream()
                .filter(o -> !o.getCreatedAt().isBefore(start) && o.getCreatedAt().isBefore(end))
                .toList();
    }

    private double computeLinearRegressionSlope(double[] x, double[] y) {
        int n = x.length;
        double sumX = Arrays.stream(x).sum();
        double sumY = Arrays.stream(y).sum();
        double sumXY = 0.0;
        double sumX2 = 0.0;

        for (int i = 0; i < n; i++) {
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }

        double numerator = (n * sumXY) - (sumX * sumY);
        double denominator = (n * sumX2) - (sumX * sumX);

        return denominator != 0 ? numerator / denominator : 0.0;
    }

    private record MerchantPaymentStats(
            int totalPayments,
            BigDecimal avgPaymentAmount,
            Double avgDaysToPay
    ) {}
}
