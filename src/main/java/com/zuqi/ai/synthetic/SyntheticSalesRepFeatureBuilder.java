package com.zuqi.ai.synthetic;

import com.zuqi.ai.feature.SalesRepFeatures;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds {@link SalesRepFeatures} from in-memory synthetic sales rep activity data.
 *
 * <p>Computation logic mirrors {@link com.zuqi.ai.feature.SalesRepFeatureServiceImpl}
 * exactly — only the data source differs.
 */
@Component
@Slf4j
public class SyntheticSalesRepFeatureBuilder {

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Compute sales rep performance features for the given period.
     *
     * @param salesRepId   the sales rep UUID
     * @param periodStart  period start (inclusive)
     * @param periodEnd    period end (inclusive)
     * @param bundle       the full in-memory dataset
     * @return fully populated {@link SalesRepFeatures}
     */
    public SalesRepFeatures computeFeatures(UUID salesRepId,
                                             LocalDateTime periodStart,
                                             LocalDateTime periodEnd,
                                             SyntheticDataBundle bundle) {
        // All activities for this rep in the period
        List<SyntheticRepActivity> activities = bundle.getRepActivities().stream()
                .filter(a -> a.salesRepId().equals(salesRepId))
                .filter(a -> !a.visitDate().isBefore(periodStart.toLocalDate())
                        && !a.visitDate().isAfter(periodEnd.toLocalDate()))
                .collect(Collectors.toList());

        // Activity records that resulted in an order
        List<SyntheticRepActivity> orderActivities = activities.stream()
                .filter(SyntheticRepActivity::orderPlaced)
                .collect(Collectors.toList());

        // Assigned merchants (distinct merchants from all activities in period)
        Set<UUID> merchantSet = activities.stream()
                .map(SyntheticRepActivity::merchantRef)
                .collect(Collectors.toSet());
        int assignedMerchants = merchantSet.size();

        // Visit and conversion metrics
        int visitCount         = activities.size();
        int visitTarget        = computeVisitTarget(periodStart, periodEnd, assignedMerchants);
        int ordersCreated      = orderActivities.size();
        double orderConversionRate = FeatureComputationUtils.computePercentage(
                ordersCreated, visitCount);

        // Order value metrics
        BigDecimal totalOrderValue = orderActivities.stream()
                .map(SyntheticRepActivity::orderValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgOrderValue = ordersCreated == 0 ? BigDecimal.ZERO :
                totalOrderValue.divide(BigDecimal.valueOf(ordersCreated), 2, RoundingMode.HALF_UP);

        // Merchant retention: merchants with at least one order placed
        Set<UUID> merchantsWhoOrdered = orderActivities.stream()
                .map(SyntheticRepActivity::merchantRef)
                .collect(Collectors.toSet());
        double merchantRetentionRate = FeatureComputationUtils.computePercentage(
                merchantsWhoOrdered.size(), assignedMerchants);

        // Collections (simplified: use order value as proxy for both target and 85% actual)
        BigDecimal collectionsTarget = totalOrderValue;
        BigDecimal collectionsActual = totalOrderValue.multiply(BigDecimal.valueOf(0.85));
        double collectionRate = collectionsTarget.compareTo(BigDecimal.ZERO) == 0 ? 0.0 :
                collectionsActual
                        .divide(collectionsTarget, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();

        // Territory metrics
        int visitedTerritoryMerchants = merchantsWhoOrdered.size();
        double territoryPenetrationPct = FeatureComputationUtils.computePercentage(
                visitedTerritoryMerchants, assignedMerchants);

        log.debug("[SyntheticSalesRepFB] rep={} visits={} orders={} period={} to {}",
                salesRepId, visitCount, ordersCreated,
                periodStart.toLocalDate(), periodEnd.toLocalDate());

        return SalesRepFeatures.builder()
                .salesRepId(salesRepId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .computedAt(periodEnd)
                // Visit and conversion
                .visitCount(visitCount)
                .visitTarget(visitTarget)
                .visitCountVsTarget(FeatureComputationUtils.computePercentage(visitCount, visitTarget))
                .ordersCreated(ordersCreated)
                .orderConversionRate(orderConversionRate)
                // Order value
                .totalOrderValue(totalOrderValue)
                .avgOrderValue(avgOrderValue)
                // Merchant metrics
                .newMerchantsAcquired(0)   // new merchant tracking not available in synthetic data
                .activeMerchants(assignedMerchants)
                .merchantRetentionRate(merchantRetentionRate)
                // Collections
                .collectionsTarget(collectionsTarget)
                .collectionsActual(collectionsActual)
                .collectionRate(collectionRate)
                .paymentsCollected(ordersCreated)
                // Route and territory
                .routeVisitsPlanned(visitTarget)
                .routeVisitsCompleted(visitCount)
                .routeAdherencePct(FeatureComputationUtils.computePercentage(visitCount, visitTarget))
                .assignedTerritoryMerchants(assignedMerchants)
                .visitedTerritoryMerchants(visitedTerritoryMerchants)
                .territoryPenetrationPct(territoryPenetrationPct)
                .build();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    /**
     * Compute the expected visit target: one visit per merchant per week.
     * Logic mirrors {@code SalesRepFeatureServiceImpl#computeVisitTarget}.
     */
    private int computeVisitTarget(LocalDateTime periodStart,
                                    LocalDateTime periodEnd,
                                    int assignedMerchantCount) {
        long daysDiff = ChronoUnit.DAYS.between(periodStart, periodEnd);
        int weeks = Math.max(1, (int) Math.ceil(daysDiff / 7.0));
        return assignedMerchantCount * weeks;
    }
}
