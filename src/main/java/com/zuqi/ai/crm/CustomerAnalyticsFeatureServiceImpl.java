package com.zuqi.ai.crm;

import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.payment.Payment;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.OrderRepository;
import com.zuqi.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes {@link CustomerAnalyticsFeatures} from JPA repositories.
 *
 * <p>This is the <em>real-data</em> path used at inference time.
 * The synthetic path (training bootstrap) is handled by
 * {@link SyntheticCustomerAnalyticsFeatureBuilder}.
 *
 * <p>Results are cached per (customerId, distributorId) to avoid repeated DB hits during
 * a batch segmentation or churn-prediction run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerAnalyticsFeatureServiceImpl {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Cacheable(value = "customerAnalyticsFeatures", key = "#customerId + ':' + #distributorId")
    public CustomerAnalyticsFeatures computeFeatures(UUID customerId, UUID distributorId) {
        log.debug("[CrmFeatures] computing features for customer={} distributor={}", customerId, distributorId);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));

        LocalDateTime now = LocalDateTime.now();

        // Load all orders for this customer in this distributor
        List<Order> allOrders = orderRepository.findByCustomerIdAndDistributorId(customerId, distributorId);

        // Non-cancelled orders only (for revenue / frequency)
        List<Order> activeOrders = allOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        // Revenue windows
        double totalRevenue90d = revenueWindow(activeOrders, now, 90);
        double revenue30d = revenueWindow(activeOrders, now, 30);
        double revenue30to60d = revenueWindowBetween(activeOrders, now, 30, 60);
        double revenue3m = revenueWindow(activeOrders, now, 90);   // 90 days ≈ 3 months
        double revenue6m = revenueWindow(activeOrders, now, 180);
        double revenue12m = revenueWindow(activeOrders, now, 365);
        double lifetimeRevenue = activeOrders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0)
                .sum();

        // Order counts
        double orderCount90d = countOrdersInWindow(activeOrders, now, 90);
        double orderCount30d = countOrdersInWindow(activeOrders, now, 30);

        // Frequency
        double orderFrequencyPerWeek = orderCount90d / 13.0;

        // Avg order value
        double avgOrderValue = activeOrders.isEmpty() ? 0.0
                : activeOrders.stream()
                        .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0)
                        .average()
                        .orElse(0.0);

        // Revenue trend slope
        double revenueTrendSlope = revenue30to60d > 0
                ? (revenue30d - revenue30to60d) / revenue30to60d
                : (revenue30d > 0 ? 1.0 : 0.0);

        // Days since last order
        int daysSinceLastOrder = activeOrders.stream()
                .map(o -> o.getCreatedAt() != null ? o.getCreatedAt() : LocalDateTime.MIN)
                .max(LocalDateTime::compareTo)
                .map(last -> (int) ChronoUnit.DAYS.between(last, now))
                .orElse(Integer.MAX_VALUE);

        // Product diversity proxy
        double productDiversityScore = orderCount90d > 0 ? Math.min(1.0, orderCount90d / 10.0) : 0.0;

        // Tenure
        int tenureMonths = customer.getCreatedAt() != null
                ? (int) ChronoUnit.MONTHS.between(customer.getCreatedAt(), now)
                : 0;

        // Category
        String customerCategory = customer.getCategory() != null && customer.getCategory().getName() != null
                ? customer.getCategory().getName()
                : "UNKNOWN";

        // Payments
        List<Payment> payments = paymentRepository.findByMerchantId(customerId, Pageable.unpaged()).getContent();
        double paymentTimelinessScore = computePaymentTimeliness(payments);

        // Credit utilization
        BigDecimal creditLimit = customer.getCreditLimit();
        double creditUtilizationPct = 0.0;
        if (creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal outstanding = orderRepository.sumOutstandingByCustomerId(customerId);
            if (outstanding != null) {
                creditUtilizationPct = outstanding.doubleValue() / creditLimit.doubleValue() * 100.0;
                creditUtilizationPct = Math.max(0.0, Math.min(100.0, creditUtilizationPct));
            }
        }

        return new CustomerAnalyticsFeatures(
                customerId,
                distributorId,
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

    private double revenueWindow(List<Order> orders, LocalDateTime now, int days) {
        LocalDateTime cutoff = now.minusDays(days);
        return orders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(cutoff))
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0)
                .sum();
    }

    private double revenueWindowBetween(List<Order> orders, LocalDateTime now, int from, int to) {
        LocalDateTime start = now.minusDays(to);
        LocalDateTime end = now.minusDays(from);
        return orders.stream()
                .filter(o -> o.getCreatedAt() != null
                        && o.getCreatedAt().isAfter(start)
                        && o.getCreatedAt().isBefore(end))
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0)
                .sum();
    }

    private double countOrdersInWindow(List<Order> orders, LocalDateTime now, int days) {
        LocalDateTime cutoff = now.minusDays(days);
        return orders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(cutoff))
                .count();
    }

    private double computePaymentTimeliness(List<Payment> payments) {
        if (payments.isEmpty()) return 100.0;
        // Consider COMPLETED payments as on-time; PENDING/FAILED as not on-time
        long onTime = payments.stream()
                .filter(p -> {
                    com.zuqi.domain.payment.PaymentStatus status = p.getStatus();
                    return status == com.zuqi.domain.payment.PaymentStatus.COMPLETED;
                })
                .count();
        return (double) onTime / payments.size() * 100.0;
    }
}
