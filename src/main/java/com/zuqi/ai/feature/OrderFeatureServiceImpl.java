package com.zuqi.ai.feature;

import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderItem;
import com.zuqi.domain.payment.Payment;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of OrderFeatureService for demand forecasting.
 *
 * Computes features used by:
 * - Demand forecasting models (XGBoost regression)
 * - AI-powered order suggestions (LLM + ML hybrid)
 *
 * Implements Kenya-specific calendar logic for holidays, paydays, and seasonal events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFeatureServiceImpl implements OrderFeatureService {

    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Cacheable(value = "demandFeatures", key = "#merchantId + ':' + #productId")
    public DemandFeatures computeFeatures(UUID merchantId, UUID productId) {
        return computeFeatures(merchantId, productId, LocalDateTime.now());
    }

    @Override
    public DemandFeatures computeFeatures(UUID merchantId, UUID productId, LocalDateTime asOfDate) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // Get historical orders for this merchant-product combination
        List<Order> merchantOrders = orderRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate);
        List<OrderItem> merchantProductItems = getOrderItemsForMerchantProduct(merchantOrders, productId);

        return DemandFeatures.builder()
                .merchantId(merchantId)
                .productId(productId)
                .computedAt(asOfDate)
                // Lag features
                .qty1wAgo(getQuantityNWeeksAgo(merchantProductItems, asOfDate, 1))
                .qty2wAgo(getQuantityNWeeksAgo(merchantProductItems, asOfDate, 2))
                .qty3wAgo(getQuantityNWeeksAgo(merchantProductItems, asOfDate, 3))
                .qty4wAgo(getQuantityNWeeksAgo(merchantProductItems, asOfDate, 4))
                .rollingAvg4w(getRollingAverage(merchantProductItems, asOfDate, 4))
                .rollingAvg12w(getRollingAverage(merchantProductItems, asOfDate, 12))
                .trendDirection(computeTrendDirection(merchantProductItems, asOfDate))
                // Temporal features
                .dayOfWeek(asOfDate.getDayOfWeek().getValue())
                .weekOfMonth(getWeekOfMonth(asOfDate))
                .monthOfYear(asOfDate.getMonthValue())
                .isHoliday(isKenyaHoliday(asOfDate.toLocalDate()))
                .isPaydayWeek(isPaydayWeek(asOfDate.toLocalDate()))
                .isRamadan(isRamadan(asOfDate.toLocalDate()))
                .isChristmasSeason(isChristmasSeason(asOfDate.toLocalDate()))
                // Merchant context
                .merchantCategory(getMerchantCategoryEncoded(merchant))
                .merchantSizeTier(computeMerchantSizeTier(merchantOrders))
                .merchantCreditStatus(computeMerchantCreditStatus(merchantId, asOfDate))
                .merchantTenureDays(computeMerchantTenureDays(merchant, asOfDate))
                // SKU context
                .productCategory(getProductCategory(product))
                .priceTier(computePriceTier(product))
                .isPromotional(isPromotional(merchantProductItems, asOfDate))
                .typicalShelfLifeDays(getTypicalShelfLifeDays(product))
                .build();
    }

    @Override
    @CacheEvict(value = "demandFeatures", key = "#merchantId + ':' + #productId")
    public void evictCache(UUID merchantId, UUID productId) {
        log.debug("Evicted demand features cache for merchant {} product {}", merchantId, productId);
    }

    @Override
    @CacheEvict(value = "demandFeatures", allEntries = true)
    public void evictMerchantCache(UUID merchantId) {
        log.debug("Evicted all demand features cache for merchant {}", merchantId);
    }

    // ==================== Lag Feature Helpers ====================

    private List<OrderItem> getOrderItemsForMerchantProduct(List<Order> orders, UUID productId) {
        return orders.stream()
                .flatMap(order -> order.getItems().stream())
                .filter(item -> item.getProduct().getId().equals(productId))
                .collect(Collectors.toList());
    }

    private BigDecimal getQuantityNWeeksAgo(List<OrderItem> items, LocalDateTime asOfDate, int weeksAgo) {
        LocalDateTime startOfWeek = asOfDate.minusWeeks(weeksAgo).with(DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS);
        LocalDateTime endOfWeek = startOfWeek.plusDays(7);

        return items.stream()
                .filter(item -> {
                    LocalDateTime itemDate = item.getOrder().getCreatedAt();
                    return !itemDate.isBefore(startOfWeek) && itemDate.isBefore(endOfWeek);
                })
                .map(OrderItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getRollingAverage(List<OrderItem> items, LocalDateTime asOfDate, int weeks) {
        LocalDateTime cutoffDate = asOfDate.minusWeeks(weeks);

        List<BigDecimal> quantities = items.stream()
                .filter(item -> !item.getOrder().getCreatedAt().isBefore(cutoffDate))
                .map(OrderItem::getQuantity)
                .collect(Collectors.toList());

        if (quantities.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = quantities.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(weeks), 2, RoundingMode.HALF_UP);
    }

    private String computeTrendDirection(List<OrderItem> items, LocalDateTime asOfDate) {
        BigDecimal avg4w = getRollingAverage(items, asOfDate, 4);
        BigDecimal avg12w = getRollingAverage(items, asOfDate, 12);

        if (avg4w.compareTo(BigDecimal.ZERO) == 0 && avg12w.compareTo(BigDecimal.ZERO) == 0) {
            return "STABLE";
        }

        // Compare recent 4 weeks to longer 12 weeks
        BigDecimal threshold = BigDecimal.valueOf(0.15); // 15% threshold
        BigDecimal diff = avg12w.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
                avg4w.subtract(avg12w).divide(avg12w, 4, RoundingMode.HALF_UP);

        if (diff.compareTo(threshold) > 0) {
            return "INCREASING";
        } else if (diff.compareTo(threshold.negate()) < 0) {
            return "DECREASING";
        } else {
            return "STABLE";
        }
    }

    // ==================== Temporal Feature Helpers ====================

    private int getWeekOfMonth(LocalDateTime date) {
        int dayOfMonth = date.getDayOfMonth();
        return (dayOfMonth - 1) / 7 + 1;
    }

    /**
     * Kenya public holidays (2026).
     * In production, this should be externalized to a database or configuration.
     */
    private boolean isKenyaHoliday(LocalDate date) {
        Set<LocalDate> holidays2026 = Set.of(
                LocalDate.of(2026, 1, 1),   // New Year's Day
                LocalDate.of(2026, 4, 3),   // Good Friday
                LocalDate.of(2026, 4, 6),   // Easter Monday
                LocalDate.of(2026, 5, 1),   // Labour Day
                LocalDate.of(2026, 6, 1),   // Madaraka Day
                LocalDate.of(2026, 10, 10), // Huduma Day
                LocalDate.of(2026, 10, 20), // Mashujaa Day
                LocalDate.of(2026, 12, 12), // Jamhuri Day
                LocalDate.of(2026, 12, 25), // Christmas Day
                LocalDate.of(2026, 12, 26)  // Boxing Day
        );

        // Add Eid holidays (approximate dates for 2026)
        Set<LocalDate> eidHolidays = Set.of(
                LocalDate.of(2026, 3, 31),  // Eid al-Fitr (approximate)
                LocalDate.of(2026, 6, 7)    // Eid al-Adha (approximate)
        );

        return holidays2026.contains(date) || eidHolidays.contains(date);
    }

    /**
     * Payday week in Kenya is typically 28th of current month to 5th of next month.
     */
    private boolean isPaydayWeek(LocalDate date) {
        int dayOfMonth = date.getDayOfMonth();
        return dayOfMonth >= 28 || dayOfMonth <= 5;
    }

    /**
     * Ramadan periods (approximate for 2026).
     * In production, calculate dynamically based on Islamic calendar.
     */
    private boolean isRamadan(LocalDate date) {
        // Ramadan 2026: approximately March 1 - March 30
        LocalDate ramadanStart = LocalDate.of(2026, 3, 1);
        LocalDate ramadanEnd = LocalDate.of(2026, 3, 30);

        return !date.isBefore(ramadanStart) && !date.isAfter(ramadanEnd);
    }

    /**
     * Christmas season in Kenya: November and December.
     */
    private boolean isChristmasSeason(LocalDate date) {
        int month = date.getMonthValue();
        return month == 11 || month == 12;
    }

    // ==================== Merchant Context Helpers ====================

    private String getMerchantCategoryEncoded(Merchant merchant) {
        if (merchant.getCategory() == null) {
            return "UNKNOWN";
        }
        return merchant.getCategory().getName();
    }

    private String computeMerchantSizeTier(List<Order> orders) {
        if (orders.isEmpty()) {
            return "SMALL";
        }

        // Classify by total order count in last 12 weeks
        long recentOrderCount = orders.stream()
                .filter(o -> o.getCreatedAt().isAfter(LocalDateTime.now().minusWeeks(12)))
                .count();

        if (recentOrderCount >= 20) {
            return "LARGE";
        } else if (recentOrderCount >= 8) {
            return "MEDIUM";
        } else {
            return "SMALL";
        }
    }

    private String computeMerchantCreditStatus(UUID merchantId, LocalDateTime asOfDate) {
        List<Payment> payments = paymentRepository.findByMerchantIdAndCreatedAtBefore(merchantId, asOfDate);

        if (payments.isEmpty()) {
            return "UNKNOWN";
        }

        // Calculate on-time payment percentage
        long onTimeCount = payments.stream()
                .filter(p -> {
                    if (p.getOrder() == null || p.getOrder().getPaymentDueDate() == null) {
                        return true; // Assume on-time if no due date
                    }
                    LocalDate dueDate = p.getOrder().getPaymentDueDate();
                    LocalDate paymentDate = p.getCreatedAt().toLocalDate();
                    return !paymentDate.isAfter(dueDate);
                })
                .count();

        double onTimeRate = (double) onTimeCount / payments.size();

        if (onTimeRate >= 0.90) {
            return "GOOD";
        } else if (onTimeRate >= 0.70) {
            return "MODERATE";
        } else {
            return "POOR";
        }
    }

    private Integer computeMerchantTenureDays(Merchant merchant, LocalDateTime asOfDate) {
        if (merchant.getCreatedAt() == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(merchant.getCreatedAt(), asOfDate);
    }

    // ==================== SKU Context Helpers ====================

    private String getProductCategory(Product product) {
        if (product.getCategory() == null) {
            return "UNKNOWN";
        }
        return product.getCategory().getName();
    }

    private String computePriceTier(Product product) {
        // Simple price tier classification
        // In production, this should be based on percentiles across all products
        BigDecimal price = product.getUnitPrice();

        if (price.compareTo(BigDecimal.valueOf(500)) < 0) {
            return "LOW";
        } else if (price.compareTo(BigDecimal.valueOf(2000)) < 0) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    private Boolean isPromotional(List<OrderItem> items, LocalDateTime asOfDate) {
        // Check if any recent orders had discounts
        LocalDateTime oneWeekAgo = asOfDate.minusWeeks(1);

        return items.stream()
                .filter(item -> !item.getOrder().getCreatedAt().isBefore(oneWeekAgo))
                .anyMatch(item -> item.getDiscountPercent() != null &&
                        item.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0);
    }

    private Integer getTypicalShelfLifeDays(Product product) {
        // Typical shelf life by category
        // In production, this should be stored in product metadata or category config
        String category = getProductCategory(product);

        return switch (category.toUpperCase()) {
            case "DAIRY" -> 7;
            case "FRESH_PRODUCE" -> 5;
            case "BAKERY" -> 3;
            case "BEVERAGES" -> 30;
            case "PACKAGED_FOODS" -> 90;
            case "HOUSEHOLD" -> 365;
            default -> 60; // Default 2 months
        };
    }
}
