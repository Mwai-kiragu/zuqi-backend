package com.zuqi.ai.feature;

import com.zuqi.domain.credit.CreditLimit;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.payment.Payment;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.CreditLimitRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantFeatureServiceImpl implements MerchantFeatureService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CreditLimitRepository creditLimitRepository;

    @Override
    @Cacheable(value = "merchantFeatures", key = "#merchantId")
    @Transactional(readOnly = true)
    public MerchantFeatures computeFeatures(UUID merchantId) {
        return computeFeatures(merchantId, LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantFeatures computeFeatures(UUID merchantId, LocalDateTime asOfDate) {
        log.debug("Computing merchant features for {} as of {}", merchantId, asOfDate);

        Customer merchant = customerRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + merchantId));

        // Fetch all data needed for feature computation
        List<Order> orders = orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate);
        List<Payment> payments = paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate);
        Optional<CreditLimit> currentCreditLimit = creditLimitRepository.findActiveLimitByMerchantId(merchantId, asOfDate);
        List<CreditLimit> creditHistory = creditLimitRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate);

        return MerchantFeatures.builder()
                .merchantId(merchantId)
                .computedAt(asOfDate)
                // Order features
                .totalOrders(computeTotalOrders(orders))
                .orderFrequencyPerWeek(computeOrderFrequencyPerWeek(orders, asOfDate))
                .avgOrderValue(computeAvgOrderValue(orders))
                .orderValueTrendSlope12w(computeOrderValueTrendSlope12w(orders, asOfDate))
                .orderConsistencyStddev(computeOrderConsistencyStddev(orders))
                .cancellationRate(computeCancellationRate(orders))
                .returnRate(computeReturnRate(orders))
                .daysSinceLastOrder(computeDaysSinceLastOrder(orders, asOfDate))
                .uniqueSkusOrdered(computeUniqueSkusOrdered(orders))
                .topSkuConcentration(computeTopSkuConcentration(orders))
                // Payment features
                .totalPayments(computeTotalPayments(payments))
                .onTimePaymentPct(computeOnTimePaymentPct(payments))
                .avgDaysToPay(computeAvgDaysToPay(payments))
                .worstDaysToPay(computeWorstDaysToPay(payments))
                .partialPaymentFrequency(computePartialPaymentFrequency(payments))
                .paymentMethodDistribution(computePaymentMethodDistribution(payments))
                .consecutiveOnTimeStreak(computeConsecutiveOnTimeStreak(payments))
                .totalOverdueAmount(computeTotalOverdueAmount(payments, asOfDate))
                // Credit features
                .currentCreditLimit(computeCurrentCreditLimit(currentCreditLimit))
                .currentUtilizationRatio(computeCurrentUtilizationRatio(currentCreditLimit, orders))
                .peakUtilizationRatio(computePeakUtilizationRatio(creditHistory, orders))
                .utilizationTrendSlope(computeUtilizationTrendSlope(creditHistory, orders, asOfDate))
                .limitIncreaseCount(computeLimitIncreaseCount(creditHistory))
                .daysSinceLastLimitChange(computeDaysSinceLastLimitChange(creditHistory, asOfDate))
                // Profile features
                .businessCategoryEncoded(computeBusinessCategoryEncoded(merchant))
                .relationshipTenureDays(computeRelationshipTenureDays(merchant, asOfDate))
                .verificationStatus(computeVerificationStatus(merchant))
                .geographicCluster(computeGeographicCluster(merchant))
                .build();
    }

    @Override
    @CacheEvict(value = "merchantFeatures", key = "#merchantId")
    public void evictCache(UUID merchantId) {
        log.debug("Evicted merchant features cache for: {}", merchantId);
    }

    // ===========================
    // Order Feature Computations
    // ===========================

    private Integer computeTotalOrders(List<Order> orders) {
        return orders.size();
    }

    private Double computeOrderFrequencyPerWeek(List<Order> orders, LocalDateTime asOfDate) {
        if (orders.isEmpty()) return 0.0;

        LocalDateTime firstOrder = orders.stream()
                .map(Order::getCreatedAt)
                .min(LocalDateTime::compareTo)
                .orElse(asOfDate);

        long daysSinceFirst = ChronoUnit.DAYS.between(firstOrder, asOfDate);
        if (daysSinceFirst == 0) return (double) orders.size();

        double weeks = daysSinceFirst / 7.0;
        return orders.size() / weeks;
    }

    private BigDecimal computeAvgOrderValue(List<Order> orders) {
        if (orders.isEmpty()) return BigDecimal.ZERO;

        BigDecimal total = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
    }

    private Double computeOrderValueTrendSlope12w(List<Order> orders, LocalDateTime asOfDate) {
        LocalDateTime twelveWeeksAgo = asOfDate.minusWeeks(12);
        List<Order> recentOrders = orders.stream()
                .filter(o -> o.getCreatedAt().isAfter(twelveWeeksAgo))
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .toList();

        if (recentOrders.size() < 2) return 0.0;

        // Simple linear regression: slope = covariance(x, y) / variance(x)
        double[] x = new double[recentOrders.size()];
        double[] y = new double[recentOrders.size()];

        for (int i = 0; i < recentOrders.size(); i++) {
            x[i] = i;  // Time index
            y[i] = recentOrders.get(i).getTotalAmount().doubleValue();
        }

        return computeLinearRegressionSlope(x, y);
    }

    private Double computeOrderConsistencyStddev(List<Order> orders) {
        if (orders.size() < 2) return 0.0;

        double[] values = orders.stream()
                .mapToDouble(o -> o.getTotalAmount().doubleValue())
                .toArray();

        return computeStandardDeviation(values);
    }

    private Double computeCancellationRate(List<Order> orders) {
        if (orders.isEmpty()) return 0.0;

        long cancelled = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                .count();

        return (double) cancelled / orders.size();
    }

    private Double computeReturnRate(List<Order> orders) {
        // TODO: Implement when return/refund tracking is added
        return 0.0;
    }

    private Integer computeDaysSinceLastOrder(List<Order> orders, LocalDateTime asOfDate) {
        return orders.stream()
                .map(Order::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .map(lastOrder -> (int) ChronoUnit.DAYS.between(lastOrder, asOfDate))
                .orElse(Integer.MAX_VALUE);  // No orders
    }

    private Integer computeUniqueSkusOrdered(List<Order> orders) {
        return (int) orders.stream()
                .flatMap(o -> o.getItems().stream())
                .map(item -> item.getProduct().getId())
                .distinct()
                .count();
    }

    private Double computeTopSkuConcentration(List<Order> orders) {
        if (orders.isEmpty()) return 0.0;

        Map<UUID, Long> skuCounts = orders.stream()
                .flatMap(o -> o.getItems().stream())
                .collect(Collectors.groupingBy(
                        item -> item.getProduct().getId(),
                        Collectors.counting()
                ));

        long maxCount = skuCounts.values().stream().max(Long::compareTo).orElse(0L);
        long totalItems = skuCounts.values().stream().mapToLong(Long::longValue).sum();

        return totalItems > 0 ? (double) maxCount / totalItems : 0.0;
    }

    // ===========================
    // Payment Feature Computations
    // ===========================

    private Integer computeTotalPayments(List<Payment> payments) {
        return payments.size();
    }

    private Double computeOnTimePaymentPct(List<Payment> payments) {
        if (payments.isEmpty()) return null;

        long onTime = payments.stream()
                .filter(this::isPaymentOnTime)
                .count();

        return (double) onTime / payments.size();
    }

    private Double computeAvgDaysToPay(List<Payment> payments) {
        List<Long> daysToPay = payments.stream()
                .map(this::computeDaysToPay)
                .filter(Objects::nonNull)
                .toList();

        if (daysToPay.isEmpty()) return null;

        return daysToPay.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private Integer computeWorstDaysToPay(List<Payment> payments) {
        return payments.stream()
                .map(this::computeDaysToPay)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .map(Long::intValue)
                .orElse(null);
    }

    private Double computePartialPaymentFrequency(List<Payment> payments) {
        if (payments.isEmpty()) return 0.0;

        long partialPayments = payments.stream()
                .filter(p -> p.getOrder() != null && p.getAmount().compareTo(p.getOrder().getTotalAmount()) < 0)
                .count();

        return (double) partialPayments / payments.size();
    }

    private Map<String, Integer> computePaymentMethodDistribution(List<Payment> payments) {
        return payments.stream()
                .filter(p -> p.getPaymentMethod() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getPaymentMethod().getName(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));
    }

    private Integer computeConsecutiveOnTimeStreak(List<Payment> payments) {
        List<Payment> sortedPayments = payments.stream()
                .sorted(Comparator.comparing(Payment::getCreatedAt).reversed())
                .toList();

        int streak = 0;
        for (Payment payment : sortedPayments) {
            if (isPaymentOnTime(payment)) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private BigDecimal computeTotalOverdueAmount(List<Payment> payments, LocalDateTime asOfDate) {
        // TODO: Implement when invoice overdue tracking is added
        return BigDecimal.ZERO;
    }

    // ===========================
    // Credit Feature Computations
    // ===========================

    private BigDecimal computeCurrentCreditLimit(Optional<CreditLimit> currentLimit) {
        return currentLimit.map(CreditLimit::getApprovedLimit).orElse(BigDecimal.ZERO);
    }

    private Double computeCurrentUtilizationRatio(Optional<CreditLimit> currentLimit, List<Order> orders) {
        if (currentLimit.isEmpty()) return null;

        BigDecimal limit = currentLimit.get().getApprovedLimit();
        if (limit.compareTo(BigDecimal.ZERO) == 0) return 0.0;

        BigDecimal currentBalance = computeCurrentCreditBalance(orders);
        return currentBalance.divide(limit, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private Double computePeakUtilizationRatio(List<CreditLimit> creditHistory, List<Order> orders) {
        // TODO: Implement historical utilization tracking
        return null;
    }

    private Double computeUtilizationTrendSlope(List<CreditLimit> creditHistory, List<Order> orders, LocalDateTime asOfDate) {
        // TODO: Implement utilization trend calculation
        return 0.0;
    }

    private Integer computeLimitIncreaseCount(List<CreditLimit> creditHistory) {
        if (creditHistory.size() < 2) return 0;

        List<CreditLimit> sorted = creditHistory.stream()
                .sorted(Comparator.comparing(CreditLimit::getCreatedAt))
                .toList();

        int increases = 0;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getApprovedLimit().compareTo(sorted.get(i - 1).getApprovedLimit()) > 0) {
                increases++;
            }
        }
        return increases;
    }

    private Integer computeDaysSinceLastLimitChange(List<CreditLimit> creditHistory, LocalDateTime asOfDate) {
        return creditHistory.stream()
                .map(CreditLimit::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .map(lastChange -> (int) ChronoUnit.DAYS.between(lastChange, asOfDate))
                .orElse(null);
    }

    // ===========================
    // Profile Feature Computations
    // ===========================

    private String computeBusinessCategoryEncoded(Customer customer) {
        return customer.getCategory() != null ? customer.getCategory().getName() : "UNKNOWN";
    }

    private Integer computeRelationshipTenureDays(Customer customer, LocalDateTime asOfDate) {
        return (int) ChronoUnit.DAYS.between(customer.getCreatedAt(), asOfDate);
    }

    private String computeVerificationStatus(Customer customer) {
        if (customer.getKycStatus() == null) return "UNVERIFIED";
        return switch (customer.getKycStatus()) {
            case APPROVED  -> "VERIFIED";
            case SUBMITTED -> "PENDING";
            default        -> "UNVERIFIED"; // PENDING, REJECTED
        };
    }

    private String computeGeographicCluster(Customer customer) {
        return customer.getCity() != null ? customer.getCity() : "UNKNOWN";
    }

    // ===========================
    // Helper Methods
    // ===========================

    private boolean isPaymentOnTime(Payment payment) {
        if (payment.getOrder() == null || payment.getOrder().getPaymentDueDate() == null) {
            return true;  // Cannot determine, assume on time
        }
        return !payment.getCreatedAt().toLocalDate().isAfter(payment.getOrder().getPaymentDueDate());
    }

    private Long computeDaysToPay(Payment payment) {
        if (payment.getOrder() == null || payment.getOrder().getCreatedAt() == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(
                payment.getOrder().getCreatedAt().toLocalDate(),
                payment.getCreatedAt().toLocalDate()
        );
    }

    private BigDecimal computeCurrentCreditBalance(List<Order> orders) {
        return orders.stream()
                .filter(o -> o.getPaidAmount().compareTo(o.getTotalAmount()) < 0)
                .map(o -> o.getTotalAmount().subtract(o.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private double computeStandardDeviation(double[] values) {
        double mean = Arrays.stream(values).average().orElse(0.0);
        double variance = Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }
}
