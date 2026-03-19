package com.zuqi.ai.crm;

import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.CustomerHealthScore;
import com.zuqi.domain.customer.Customer;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.CustomerHealthScoreRepository;
import com.zuqi.repository.CustomerRepository;
import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Computes composite customer health scores from analytics features.
 *
 * <p>The health score aggregates five sub-dimensions (each 0–100) into a single score,
 * then maps it to a named tier for field-sales prioritisation.
 *
 * <h3>Sub-dimensions and weights:</h3>
 * <ul>
 *   <li>Order frequency (25%) — how often the customer orders</li>
 *   <li>Payment timeliness (25%) — share of on-time payments</li>
 *   <li>Revenue trend (20%) — is the customer growing or declining?</li>
 *   <li>Engagement / recency (15%) — days since last order</li>
 *   <li>Credit health (15%) — credit utilisation headroom</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerHealthScoreService {

    private final CustomerRepository customerRepository;
    private final CustomerAnalyticsFeatureServiceImpl featureService;
    private final CustomerHealthScoreRepository healthScoreRepository;
    private final DistributorRepository distributorRepository;

    /**
     * Compute and persist a health score for a single customer.
     *
     * @param customerId    customer to score
     * @param distributorId distributor context
     * @return saved health score entity
     */
    @Transactional
    public CustomerHealthScore computeScore(UUID customerId, UUID distributorId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        CustomerAnalyticsFeatures f = featureService.computeFeatures(customerId, distributorId);

        // Sub-scores (0–100)
        double orderFreqScore = Math.min(100.0, f.orderFrequencyPerWeek() * 25.0);
        double paymentScore = f.paymentTimelinessScore();  // already 0–100
        double revenueTrendScore = Math.min(100.0, Math.max(0.0, 50.0 + f.revenueTrendSlope() * 50.0));
        double engagementScore = Math.max(0.0, 100.0 - f.daysSinceLastOrder() * 3.0);
        double creditHealthScore = Math.max(0.0, 100.0 - f.creditUtilizationPct());

        // Composite
        double healthScore = orderFreqScore * 0.25
                + paymentScore * 0.25
                + revenueTrendScore * 0.20
                + engagementScore * 0.15
                + creditHealthScore * 0.15;

        String tier = computeTier(healthScore);
        String dataPhase = DataPhaseTracker.MODEL_CUSTOMER_HEALTH_SCORER; // store as model reference

        Optional<CustomerHealthScore> existing =
                healthScoreRepository.findByDistributorIdAndCustomerId(distributorId, customerId);

        CustomerHealthScore entity = existing.orElseGet(() -> CustomerHealthScore.builder()
                .distributor(distributor)
                .customer(customer)
                .build());

        entity.setHealthScore(healthScore);
        entity.setHealthTier(tier);
        entity.setOrderFrequencyScore(orderFreqScore);
        entity.setPaymentTimelinessScore(paymentScore);
        entity.setRevenueTrendScore(revenueTrendScore);
        entity.setEngagementScore(engagementScore);
        entity.setCreditHealthScore(creditHealthScore);
        entity.setDataPhase(dataPhase);
        entity.setComputedAt(LocalDateTime.now());

        return healthScoreRepository.save(entity);
    }

    /**
     * Compute health scores for all active customers in the distributor.
     *
     * @param distributorId distributor to process
     * @return count of scores computed
     */
    @Transactional
    public int computeAll(UUID distributorId) {
        List<Customer> customers = customerRepository.findByDistributorIdAndActiveTrue(distributorId);
        int count = 0;
        for (Customer c : customers) {
            try {
                computeScore(c.getId(), distributorId);
                count++;
            } catch (Exception e) {
                log.warn("[HealthScore] Failed for customer={}: {}", c.getId(), e.getMessage());
            }
        }
        return count;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String computeTier(double score) {
        if (score >= 80) return "THRIVING";
        if (score >= 60) return "HEALTHY";
        if (score >= 40) return "NEEDS_ATTENTION";
        if (score >= 20) return "AT_RISK";
        return "CRITICAL";
    }
}
