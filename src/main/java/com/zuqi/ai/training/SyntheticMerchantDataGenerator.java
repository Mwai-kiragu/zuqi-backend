package com.zuqi.ai.training;

import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.training.MerchantArchetype.Range;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Generates synthetic merchant profiles for ML model training.
 *
 * Creates realistic merchant data based on defined archetypes
 * when real outcome data is not yet available.
 *
 * Distribution strategy:
 * - 40% Excellent Retailer (5% default)
 * - 30% Good Hardware Store (10% default)
 * - 20% Average Shop (20% default)
 * -  7% Risky Newcomer (35% default)
 * -  2% Struggling Business (50% default)
 * -  1% High Default Risk (75% default)
 *
 * Overall expected default rate: ~15%
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 1
 */
@Service
@Slf4j
public class SyntheticMerchantDataGenerator {

    private final Random random = new Random();

    private static final Map<String, Integer> PAYMENT_METHODS = Map.of(
            "MPESA", 60,
            "CASH", 30,
            "BANK_TRANSFER", 10
    );

    private static final List<String> ALL_CITIES = List.of(
            "Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret",
            "Thika", "Ruiru", "Machakos", "Nyeri", "Meru"
    );

    /**
     * Generate a dataset of synthetic merchants.
     *
     * @param size Total number of merchants to generate
     * @return List of synthetic merchants with features and labels
     */
    public List<SyntheticMerchant> generateDataset(int size) {
        log.info("Generating synthetic dataset of {} merchants", size);
        long startTime = System.currentTimeMillis();

        List<SyntheticMerchant> merchants = new ArrayList<>(size);
        List<MerchantArchetype> archetypes = MerchantArchetype.allArchetypes();

        // Distribution weights: [40%, 30%, 20%, 7%, 2%, 1%]
        double[] distribution = {0.40, 0.30, 0.20, 0.07, 0.02, 0.01};

        for (int i = 0; i < size; i++) {
            MerchantArchetype archetype = selectArchetype(archetypes, distribution);
            MerchantFeatures features = generateFeatures(archetype);
            boolean didDefault = simulateOutcome(archetype, features);

            merchants.add(SyntheticMerchant.builder()
                    .features(features)
                    .didDefault(didDefault)
                    .archetypeName(archetype.name())
                    .defaultProbability(archetype.defaultProbability())
                    .build());
        }

        long duration = System.currentTimeMillis() - startTime;
        long defaultCount = merchants.stream().filter(SyntheticMerchant::didDefault).count();
        double defaultRate = (double) defaultCount / size;

        log.info("Generated {} synthetic merchants in {}ms ({}% default rate)",
                size, duration, String.format("%.1f", defaultRate * 100));

        return merchants;
    }

    /**
     * Select archetype based on distribution weights.
     */
    private MerchantArchetype selectArchetype(List<MerchantArchetype> archetypes,
                                               double[] distribution) {
        double rand = random.nextDouble();
        double cumulative = 0.0;

        for (int i = 0; i < archetypes.size(); i++) {
            cumulative += distribution[i];
            if (rand < cumulative) {
                return archetypes.get(i);
            }
        }

        return archetypes.get(archetypes.size() - 1); // Fallback
    }

    /**
     * Generate merchant features from archetype distributions.
     */
    private MerchantFeatures generateFeatures(MerchantArchetype archetype) {
        // Generate UUID for synthetic merchant
        UUID merchantId = UUID.randomUUID();

        // Sample from distributions with realistic noise
        return MerchantFeatures.builder()
                .merchantId(merchantId)
                .computedAt(LocalDateTime.now())
                // Order features
                .totalOrders(sampleInt(archetype.totalOrdersRange()))
                .orderFrequencyPerWeek(sampleDouble(archetype.orderFrequencyRange()))
                .avgOrderValue(sampleBigDecimal(archetype.avgOrderValueRange()))
                .orderValueTrendSlope12w(sampleDouble(archetype.orderTrendSlopeRange()))
                .orderConsistencyStddev(generateOrderConsistency(archetype))
                .cancellationRate(sampleDouble(archetype.cancellationRateRange()))
                .returnRate(sampleDouble(archetype.returnRateRange()))
                .daysSinceLastOrder(sampleInt(archetype.daysSinceLastOrderRange()))
                .uniqueSkusOrdered(sampleInt(archetype.uniqueSkusOrderedRange()))
                .topSkuConcentration(sampleDouble(archetype.topSkuConcentrationRange()))
                // Payment features
                .totalPayments(generateTotalPayments(archetype))
                .onTimePaymentPct(sampleDouble(archetype.onTimePaymentPctRange()))
                .avgDaysToPay(sampleDouble(archetype.avgDaysToPayRange()))
                .worstDaysToPay(sampleInt(archetype.worstDaysToPayRange()))
                .partialPaymentFrequency(sampleDouble(archetype.partialPaymentFrequencyRange()))
                .paymentMethodDistribution(generatePaymentMethods())
                .consecutiveOnTimeStreak(sampleInt(archetype.consecutiveOnTimeStreakRange()))
                .totalOverdueAmount(sampleBigDecimal(archetype.totalOverdueAmountRange()))
                // Credit features
                .currentCreditLimit(sampleBigDecimal(archetype.currentCreditLimitRange()))
                .currentUtilizationRatio(sampleDouble(archetype.currentUtilizationRatioRange()))
                .peakUtilizationRatio(sampleDouble(archetype.peakUtilizationRatioRange()))
                .utilizationTrendSlope(sampleDouble(archetype.utilizationTrendSlopeRange()))
                .limitIncreaseCount(sampleInt(archetype.limitIncreaseCountRange()))
                .daysSinceLastLimitChange(sampleInt(archetype.daysSinceLastLimitChangeRange()))
                // Profile features
                .businessCategoryEncoded(sampleFromList(archetype.preferredCategories()))
                .relationshipTenureDays(sampleInt(archetype.tenureDaysRange()))
                .verificationStatus(sampleFromList(archetype.verificationStatuses()))
                .geographicCluster(sampleFromList(archetype.preferredCities()))
                .build();
    }

    /**
     * Simulate merchant outcome based on archetype + feature adjustments.
     */
    private boolean simulateOutcome(MerchantArchetype archetype, MerchantFeatures features) {
        // Base probability from archetype
        double defaultProb = archetype.defaultProbability();

        // Adjust based on specific feature values (add realism)
        if (features.worstDaysToPay() > 60) {
            defaultProb += 0.15; // Severe overdue increases risk
        }

        if (features.currentUtilizationRatio() > 0.85) {
            defaultProb += 0.10; // High utilization increases risk
        }

        if (features.relationshipTenureDays() < 60) {
            defaultProb += 0.05; // Very new merchants higher risk
        }

        if (features.onTimePaymentPct() < 0.65) {
            defaultProb += 0.20; // Poor payment history
        }

        if (features.orderValueTrendSlope12w() < -0.20) {
            defaultProb += 0.08; // Declining business
        }

        // Cap at 95% (always some uncertainty)
        defaultProb = Math.min(defaultProb, 0.95);

        return random.nextDouble() < defaultProb;
    }

    /**
     * Generate order consistency (stddev) relative to avg order value.
     */
    private Double generateOrderConsistency(MerchantArchetype archetype) {
        BigDecimal avgValue = sampleBigDecimal(archetype.avgOrderValueRange());
        // Stddev typically 10-40% of mean for realistic variation
        double coefficient = 0.10 + (random.nextDouble() * 0.30);
        return avgValue.doubleValue() * coefficient;
    }

    /**
     * Generate total payments (typically ~90% of total orders).
     */
    private Integer generateTotalPayments(MerchantArchetype archetype) {
        int totalOrders = sampleInt(archetype.totalOrdersRange());
        double paymentRate = 0.85 + (random.nextDouble() * 0.10); // 85-95%
        return (int) Math.round(totalOrders * paymentRate);
    }

    /**
     * Generate payment method distribution with realistic proportions.
     */
    private Map<String, Integer> generatePaymentMethods() {
        Map<String, Integer> distribution = new HashMap<>();

        // Base distribution with random variation
        int total = 40 + random.nextInt(80); // 40-120 total payments

        // M-Pesa dominant in Kenya FMCG market
        int mpesaPct = 50 + random.nextInt(30); // 50-80%
        int mpesa = (total * mpesaPct) / 100;

        // Cash
        int cashPct = 15 + random.nextInt(25); // 15-40%
        int cash = (total * cashPct) / 100;

        // Bank transfer (remainder)
        int bank = total - mpesa - cash;

        distribution.put("MPESA", Math.max(0, mpesa));
        distribution.put("CASH", Math.max(0, cash));
        distribution.put("BANK_TRANSFER", Math.max(0, bank));

        return distribution;
    }

    // ========== Sampling Utilities ==========

    private Integer sampleInt(Range<Integer> range) {
        int min = range.min();
        int max = range.max();
        return min + random.nextInt(max - min + 1);
    }

    private Double sampleDouble(Range<Double> range) {
        double min = range.min();
        double max = range.max();
        double value = min + (random.nextDouble() * (max - min));
        return Math.round(value * 100.0) / 100.0; // 2 decimal places
    }

    private BigDecimal sampleBigDecimal(Range<BigDecimal> range) {
        BigDecimal min = range.min();
        BigDecimal max = range.max();
        BigDecimal delta = max.subtract(min);

        double randomValue = random.nextDouble();
        BigDecimal value = min.add(delta.multiply(BigDecimal.valueOf(randomValue)));

        // Round to nearest 100 KES for realism
        return value.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private <T> T sampleFromList(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Validate generated dataset quality.
     *
     * Checks:
     * - Default rate in expected range (12-18%)
     * - All features populated
     * - Realistic value ranges
     * - No impossible correlations
     */
    public DatasetQualityReport validateDataset(List<SyntheticMerchant> merchants) {
        log.info("Validating dataset quality for {} merchants", merchants.size());

        long defaultCount = merchants.stream()
                .filter(SyntheticMerchant::didDefault)
                .count();
        double defaultRate = (double) defaultCount / merchants.size();

        // Check default rate
        boolean defaultRateOk = defaultRate >= 0.12 && defaultRate <= 0.18;

        // Check feature completeness
        boolean allFeaturesPopulated = merchants.stream()
                .allMatch(m -> m.features().totalOrders() != null &&
                              m.features().onTimePaymentPct() != null &&
                              m.features().currentCreditLimit() != null);

        // Check realistic value ranges
        boolean realisticValues = merchants.stream()
                .allMatch(m -> m.features().onTimePaymentPct() >= 0.0 &&
                              m.features().onTimePaymentPct() <= 1.0 &&
                              m.features().currentUtilizationRatio() >= 0.0 &&
                              m.features().currentUtilizationRatio() <= 1.05); // Allow slight over-limit

        // Check correlations (on-time payment % should correlate with defaults)
        double avgOnTimePctDefault = merchants.stream()
                .filter(SyntheticMerchant::didDefault)
                .mapToDouble(m -> m.features().onTimePaymentPct())
                .average()
                .orElse(0.0);

        double avgOnTimePctNoDefault = merchants.stream()
                .filter(m -> !m.didDefault())
                .mapToDouble(m -> m.features().onTimePaymentPct())
                .average()
                .orElse(0.0);

        boolean correlationOk = avgOnTimePctDefault < avgOnTimePctNoDefault - 0.10; // At least 10% difference

        boolean isValid = defaultRateOk && allFeaturesPopulated && realisticValues && correlationOk;

        DatasetQualityReport report = new DatasetQualityReport(
                merchants.size(),
                defaultRate,
                defaultRateOk,
                allFeaturesPopulated,
                realisticValues,
                correlationOk,
                isValid
        );

        if (isValid) {
            log.info("Dataset validation PASSED: {} merchants, {:.1f}% default rate",
                    merchants.size(), defaultRate * 100);
        } else {
            log.warn("Dataset validation FAILED: defaultRate={}, features={}, values={}, correlation={}",
                    defaultRateOk, allFeaturesPopulated, realisticValues, correlationOk);
        }

        return report;
    }

    /**
     * Dataset quality validation report.
     */
    public record DatasetQualityReport(
            int totalMerchants,
            double defaultRate,
            boolean defaultRateOk,
            boolean allFeaturesPopulated,
            boolean realisticValues,
            boolean correlationOk,
            boolean isValid
    ) {
    }
}
