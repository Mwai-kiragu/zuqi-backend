package com.zuqi.ai.feature;

import com.zuqi.domain.merchant.Merchant;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.payment.Payment;
import com.zuqi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of SalesRepFeatureService for sales rep performance analysis.
 *
 * Computes features used by:
 * - Sales rep underperformance detection (XGBoost regression)
 * - Performance prediction and alerting
 *
 * Note: Some features (visit tracking, route adherence) require future enhancements
 * when Visit and Route entities are added to the system.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalesRepFeatureServiceImpl implements SalesRepFeatureService {

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Cacheable(value = "salesRepFeatures", key = "#salesRepId + ':' + #periodStart + ':' + #periodEnd")
    public SalesRepFeatures computeFeatures(UUID salesRepId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        // Validate sales rep exists
        userRepository.findById(salesRepId)
                .orElseThrow(() -> new IllegalArgumentException("Sales rep not found: " + salesRepId));

        // Get all orders created by this sales rep in the period
        List<Order> orders = getOrdersForPeriod(salesRepId, periodStart, periodEnd);

        // Get all payments collected by this sales rep in the period
        List<Payment> payments = getPaymentsForPeriod(salesRepId, periodStart, periodEnd);

        // Get assigned merchants
        List<Merchant> assignedMerchants = getAssignedMerchants(salesRepId);

        // Get new merchants acquired in period
        List<Merchant> newMerchants = getNewMerchantsInPeriod(salesRepId, periodStart, periodEnd);

        // Compute metrics
        int visitCount = computeVisitCount(orders, assignedMerchants);
        int visitTarget = computeVisitTarget(periodStart, periodEnd, assignedMerchants.size());

        return SalesRepFeatures.builder()
                .salesRepId(salesRepId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .computedAt(LocalDateTime.now())
                // Visit and conversion metrics
                .visitCount(visitCount)
                .visitTarget(visitTarget)
                .visitCountVsTarget(computePercentage(visitCount, visitTarget))
                .ordersCreated(orders.size())
                .orderConversionRate(computePercentage(orders.size(), visitCount))
                // Order value metrics
                .totalOrderValue(computeTotalOrderValue(orders))
                .avgOrderValue(computeAvgOrderValue(orders))
                // Merchant metrics
                .newMerchantsAcquired(newMerchants.size())
                .activeMerchants(assignedMerchants.size())
                .merchantRetentionRate(computeMerchantRetentionRate(orders, assignedMerchants))
                // Collection and payment metrics
                .collectionsTarget(computeCollectionsTarget(orders))
                .collectionsActual(computeCollectionsActual(payments))
                .collectionRate(computeCollectionRate(payments, orders))
                .paymentsCollected(payments.size())
                // Route and territory metrics
                .routeVisitsPlanned(computeRouteVisitsPlanned(assignedMerchants, periodStart, periodEnd))
                .routeVisitsCompleted(visitCount)
                .routeAdherencePct(computePercentage(visitCount, computeRouteVisitsPlanned(assignedMerchants, periodStart, periodEnd)))
                .assignedTerritoryMerchants(assignedMerchants.size())
                .visitedTerritoryMerchants(computeVisitedMerchants(orders))
                .territoryPenetrationPct(computePercentage(computeVisitedMerchants(orders), assignedMerchants.size()))
                .build();
    }

    @Override
    @CacheEvict(value = "salesRepFeatures", key = "#salesRepId + ':' + #periodStart + ':' + #periodEnd")
    public void evictCache(UUID salesRepId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        log.debug("Evicted sales rep features cache for rep {} period {}-{}", salesRepId, periodStart, periodEnd);
    }

    @Override
    @CacheEvict(value = "salesRepFeatures", allEntries = true)
    public void evictRepCache(UUID salesRepId) {
        log.debug("Evicted all sales rep features cache for rep {}", salesRepId);
    }

    // ==================== Helper Methods ====================

    private List<Order> getOrdersForPeriod(UUID salesRepId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        // Get all orders where sales rep is assigned and created within period
        return orderRepository.findAll().stream()
                .filter(o -> o.getSalesRep() != null && o.getSalesRep().getId().equals(salesRepId))
                .filter(o -> !o.getCreatedAt().isBefore(periodStart) && !o.getCreatedAt().isAfter(periodEnd))
                .collect(Collectors.toList());
    }

    private List<Payment> getPaymentsForPeriod(UUID salesRepId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        // Get all payments where the order's sales rep matches and payment created within period
        return paymentRepository.findAll().stream()
                .filter(p -> p.getOrder() != null && p.getOrder().getSalesRep() != null)
                .filter(p -> p.getOrder().getSalesRep().getId().equals(salesRepId))
                .filter(p -> !p.getCreatedAt().isBefore(periodStart) && !p.getCreatedAt().isAfter(periodEnd))
                .collect(Collectors.toList());
    }

    private List<Merchant> getAssignedMerchants(UUID salesRepId) {
        // Get all merchants assigned to this sales rep
        return merchantRepository.findAll().stream()
                .filter(m -> m.getAssignedSalesRep() != null && m.getAssignedSalesRep().getId().equals(salesRepId))
                .filter(Merchant::isActive)
                .collect(Collectors.toList());
    }

    private List<Merchant> getNewMerchantsInPeriod(UUID salesRepId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        return merchantRepository.findAll().stream()
                .filter(m -> m.getAssignedSalesRep() != null && m.getAssignedSalesRep().getId().equals(salesRepId))
                .filter(m -> m.getCreatedAt() != null)
                .filter(m -> !m.getCreatedAt().isBefore(periodStart) && !m.getCreatedAt().isAfter(periodEnd))
                .collect(Collectors.toList());
    }

    /**
     * Estimates visit count based on unique merchant-days with orders.
     * TODO: Replace with actual Visit entity tracking when implemented.
     */
    private int computeVisitCount(List<Order> orders, List<Merchant> assignedMerchants) {
        // Estimate visits as unique merchant IDs in orders
        // In reality, a visit might not result in an order
        Set<UUID> merchantsWithOrders = orders.stream()
                .map(o -> o.getMerchant().getId())
                .collect(Collectors.toSet());

        // Assume 1 visit per merchant that placed an order
        return merchantsWithOrders.size();
    }

    /**
     * Computes visit target based on assigned merchants and period duration.
     * Assumes target is to visit each merchant once per week.
     */
    private int computeVisitTarget(LocalDateTime periodStart, LocalDateTime periodEnd, int assignedMerchantCount) {
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd);
        int weeks = Math.max(1, (int) Math.ceil(daysDiff / 7.0));

        // Target: visit each merchant once per week
        return assignedMerchantCount * weeks;
    }

    private BigDecimal computeTotalOrderValue(List<Order> orders) {
        return orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal computeAvgOrderValue(List<Order> orders) {
        if (orders.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = computeTotalOrderValue(orders);
        return total.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
    }

    private Double computeMerchantRetentionRate(List<Order> orders, List<Merchant> assignedMerchants) {
        if (assignedMerchants.isEmpty()) {
            return 0.0;
        }

        Set<UUID> merchantsWhoOrdered = orders.stream()
                .map(o -> o.getMerchant().getId())
                .collect(Collectors.toSet());

        int activeCount = (int) assignedMerchants.stream()
                .filter(m -> merchantsWhoOrdered.contains(m.getId()))
                .count();

        return computePercentage(activeCount, assignedMerchants.size());
    }

    private BigDecimal computeCollectionsTarget(List<Order> orders) {
        // Collections target is the total value of all orders in the period
        return computeTotalOrderValue(orders);
    }

    private BigDecimal computeCollectionsActual(List<Payment> payments) {
        return payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Double computeCollectionRate(List<Payment> payments, List<Order> orders) {
        BigDecimal target = computeCollectionsTarget(orders);
        BigDecimal actual = computeCollectionsActual(payments);

        if (target.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }

        return actual.divide(target, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    /**
     * Computes planned route visits.
     * TODO: Replace with actual Route entity tracking when implemented.
     */
    private int computeRouteVisitsPlanned(List<Merchant> assignedMerchants, LocalDateTime periodStart, LocalDateTime periodEnd) {
        // Use same logic as visit target for now
        return computeVisitTarget(periodStart, periodEnd, assignedMerchants.size());
    }

    private int computeVisitedMerchants(List<Order> orders) {
        // Unique merchants who placed orders in the period
        return (int) orders.stream()
                .map(o -> o.getMerchant().getId())
                .distinct()
                .count();
    }

    private Double computePercentage(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }

        return ((double) numerator / denominator) * 100.0;
    }
}
