package com.zuqi.ai.training;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Merchant archetype definition for synthetic data generation.
 *
 * Represents a risk profile with realistic feature distributions
 * for generating training data.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 1.1
 */
@Builder
public record MerchantArchetype(
        String name,
        double defaultProbability,

        // Order distributions
        Range<Integer> totalOrdersRange,
        Range<Double> orderFrequencyRange,          // Orders per week
        Range<BigDecimal> avgOrderValueRange,       // KES
        Range<Double> orderTrendSlopeRange,         // Growth rate
        Range<Double> cancellationRateRange,
        Range<Double> returnRateRange,
        Range<Integer> daysSinceLastOrderRange,
        Range<Integer> uniqueSkusOrderedRange,
        Range<Double> topSkuConcentrationRange,

        // Payment distributions
        Range<Double> onTimePaymentPctRange,
        Range<Double> avgDaysToPayRange,
        Range<Integer> worstDaysToPayRange,
        Range<Double> partialPaymentFrequencyRange,
        Range<Integer> consecutiveOnTimeStreakRange,
        Range<BigDecimal> totalOverdueAmountRange,

        // Credit distributions
        Range<BigDecimal> currentCreditLimitRange,  // KES
        Range<Double> currentUtilizationRatioRange,
        Range<Double> peakUtilizationRatioRange,
        Range<Double> utilizationTrendSlopeRange,
        Range<Integer> limitIncreaseCountRange,
        Range<Integer> daysSinceLastLimitChangeRange,

        // Profile
        Range<Integer> tenureDaysRange,
        List<String> preferredCategories,
        List<String> preferredCities,
        List<String> verificationStatuses
) {

    /**
     * Simple range container for min/max values.
     */
    public record Range<T extends Comparable<T>>(T min, T max) {
        public Range {
            if (min.compareTo(max) > 0) {
                throw new IllegalArgumentException("Min must be <= max");
            }
        }
    }

    /**
     * Create the "Excellent Retailer" archetype.
     * Characteristics: 95% on-time, high frequency, stable orders, long tenure
     * Default risk: 5%
     */
    public static MerchantArchetype excellentRetailer() {
        return MerchantArchetype.builder()
                .name("Excellent Retailer")
                .defaultProbability(0.05)
                // Orders: High frequency, stable, growing
                .totalOrdersRange(new Range<>(60, 150))
                .orderFrequencyRange(new Range<>(3.0, 5.0))
                .avgOrderValueRange(new Range<>(BigDecimal.valueOf(40_000), BigDecimal.valueOf(100_000)))
                .orderTrendSlopeRange(new Range<>(0.05, 0.25))
                .cancellationRateRange(new Range<>(0.0, 0.02))
                .returnRateRange(new Range<>(0.0, 0.01))
                .daysSinceLastOrderRange(new Range<>(1, 5))
                .uniqueSkusOrderedRange(new Range<>(25, 60))
                .topSkuConcentrationRange(new Range<>(0.15, 0.35))
                // Payments: Excellent
                .onTimePaymentPctRange(new Range<>(0.93, 0.99))
                .avgDaysToPayRange(new Range<>(3.0, 10.0))
                .worstDaysToPayRange(new Range<>(7, 20))
                .partialPaymentFrequencyRange(new Range<>(0.0, 0.03))
                .consecutiveOnTimeStreakRange(new Range<>(8, 20))
                .totalOverdueAmountRange(new Range<>(BigDecimal.ZERO, BigDecimal.valueOf(5_000)))
                // Credit: Healthy utilization
                .currentCreditLimitRange(new Range<>(BigDecimal.valueOf(200_000), BigDecimal.valueOf(800_000)))
                .currentUtilizationRatioRange(new Range<>(0.25, 0.55))
                .peakUtilizationRatioRange(new Range<>(0.50, 0.75))
                .utilizationTrendSlopeRange(new Range<>(-0.10, 0.05))
                .limitIncreaseCountRange(new Range<>(1, 4))
                .daysSinceLastLimitChangeRange(new Range<>(30, 180))
                // Profile: Established
                .tenureDaysRange(new Range<>(365, 1095))
                .preferredCategories(List.of("Hardware Store", "General Store", "Supermarket"))
                .preferredCities(List.of("Nairobi", "Mombasa", "Kisumu", "Nakuru"))
                .verificationStatuses(List.of("VERIFIED"))
                .build();
    }

    /**
     * Create the "Good Hardware Store" archetype.
     * Characteristics: 90% on-time, moderate frequency, growing orders
     * Default risk: 10%
     */
    public static MerchantArchetype goodHardwareStore() {
        return MerchantArchetype.builder()
                .name("Good Hardware Store")
                .defaultProbability(0.10)
                .totalOrdersRange(new Range<>(40, 90))
                .orderFrequencyRange(new Range<>(2.0, 4.0))
                .avgOrderValueRange(new Range<>(BigDecimal.valueOf(30_000), BigDecimal.valueOf(70_000)))
                .orderTrendSlopeRange(new Range<>(0.0, 0.20))
                .cancellationRateRange(new Range<>(0.01, 0.04))
                .returnRateRange(new Range<>(0.01, 0.03))
                .daysSinceLastOrderRange(new Range<>(2, 8))
                .uniqueSkusOrderedRange(new Range<>(20, 45))
                .topSkuConcentrationRange(new Range<>(0.20, 0.40))
                .onTimePaymentPctRange(new Range<>(0.85, 0.95))
                .avgDaysToPayRange(new Range<>(7.0, 18.0))
                .worstDaysToPayRange(new Range<>(15, 35))
                .partialPaymentFrequencyRange(new Range<>(0.02, 0.08))
                .consecutiveOnTimeStreakRange(new Range<>(4, 12))
                .totalOverdueAmountRange(new Range<>(BigDecimal.ZERO, BigDecimal.valueOf(15_000)))
                .currentCreditLimitRange(new Range<>(BigDecimal.valueOf(150_000), BigDecimal.valueOf(500_000)))
                .currentUtilizationRatioRange(new Range<>(0.35, 0.65))
                .peakUtilizationRatioRange(new Range<>(0.60, 0.85))
                .utilizationTrendSlopeRange(new Range<>(-0.05, 0.10))
                .limitIncreaseCountRange(new Range<>(1, 3))
                .daysSinceLastLimitChangeRange(new Range<>(45, 270))
                .tenureDaysRange(new Range<>(180, 730))
                .preferredCategories(List.of("Hardware Store", "Building Materials"))
                .preferredCities(List.of("Nairobi", "Nakuru", "Eldoret", "Thika"))
                .verificationStatuses(List.of("VERIFIED"))
                .build();
    }

    /**
     * Create the "Average Shop" archetype.
     * Characteristics: 80% on-time, variable frequency, flat trend
     * Default risk: 20%
     */
    public static MerchantArchetype averageShop() {
        return MerchantArchetype.builder()
                .name("Average Shop")
                .defaultProbability(0.20)
                .totalOrdersRange(new Range<>(25, 60))
                .orderFrequencyRange(new Range<>(1.5, 3.0))
                .avgOrderValueRange(new Range<>(BigDecimal.valueOf(20_000), BigDecimal.valueOf(50_000)))
                .orderTrendSlopeRange(new Range<>(-0.05, 0.10))
                .cancellationRateRange(new Range<>(0.03, 0.08))
                .returnRateRange(new Range<>(0.02, 0.05))
                .daysSinceLastOrderRange(new Range<>(3, 12))
                .uniqueSkusOrderedRange(new Range<>(15, 35))
                .topSkuConcentrationRange(new Range<>(0.25, 0.50))
                .onTimePaymentPctRange(new Range<>(0.75, 0.88))
                .avgDaysToPayRange(new Range<>(12.0, 25.0))
                .worstDaysToPayRange(new Range<>(25, 50))
                .partialPaymentFrequencyRange(new Range<>(0.05, 0.15))
                .consecutiveOnTimeStreakRange(new Range<>(2, 8))
                .totalOverdueAmountRange(new Range<>(BigDecimal.ZERO, BigDecimal.valueOf(30_000)))
                .currentCreditLimitRange(new Range<>(BigDecimal.valueOf(100_000), BigDecimal.valueOf(350_000)))
                .currentUtilizationRatioRange(new Range<>(0.45, 0.75))
                .peakUtilizationRatioRange(new Range<>(0.70, 0.95))
                .utilizationTrendSlopeRange(new Range<>(-0.03, 0.15))
                .limitIncreaseCountRange(new Range<>(0, 2))
                .daysSinceLastLimitChangeRange(new Range<>(60, 365))
                .tenureDaysRange(new Range<>(90, 365))
                .preferredCategories(List.of("General Store", "Kiosk", "Grocery"))
                .preferredCities(List.of("Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret", "Thika"))
                .verificationStatuses(List.of("VERIFIED", "PENDING"))
                .build();
    }

    /**
     * Create the "Risky Newcomer" archetype.
     * Characteristics: 70% on-time, low frequency, erratic orders, <90 days tenure
     * Default risk: 35%
     */
    public static MerchantArchetype riskyNewcomer() {
        return MerchantArchetype.builder()
                .name("Risky Newcomer")
                .defaultProbability(0.35)
                .totalOrdersRange(new Range<>(8, 30))
                .orderFrequencyRange(new Range<>(1.0, 2.5))
                .avgOrderValueRange(new Range<>(BigDecimal.valueOf(15_000), BigDecimal.valueOf(40_000)))
                .orderTrendSlopeRange(new Range<>(-0.15, 0.15))
                .cancellationRateRange(new Range<>(0.05, 0.15))
                .returnRateRange(new Range<>(0.03, 0.10))
                .daysSinceLastOrderRange(new Range<>(5, 20))
                .uniqueSkusOrderedRange(new Range<>(8, 25))
                .topSkuConcentrationRange(new Range<>(0.30, 0.60))
                .onTimePaymentPctRange(new Range<>(0.60, 0.78))
                .avgDaysToPayRange(new Range<>(18.0, 35.0))
                .worstDaysToPayRange(new Range<>(35, 65))
                .partialPaymentFrequencyRange(new Range<>(0.10, 0.25))
                .consecutiveOnTimeStreakRange(new Range<>(1, 5))
                .totalOverdueAmountRange(new Range<>(BigDecimal.ZERO, BigDecimal.valueOf(50_000)))
                .currentCreditLimitRange(new Range<>(BigDecimal.valueOf(50_000), BigDecimal.valueOf(200_000)))
                .currentUtilizationRatioRange(new Range<>(0.55, 0.85))
                .peakUtilizationRatioRange(new Range<>(0.75, 1.00))
                .utilizationTrendSlopeRange(new Range<>(0.0, 0.25))
                .limitIncreaseCountRange(new Range<>(0, 1))
                .daysSinceLastLimitChangeRange(new Range<>(15, 120))
                .tenureDaysRange(new Range<>(30, 89))
                .preferredCategories(List.of("Kiosk", "General Store", "Grocery"))
                .preferredCities(List.of("Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret", "Thika", "Ruiru", "Machakos"))
                .verificationStatuses(List.of("VERIFIED", "PENDING"))
                .build();
    }

    /**
     * Create the "Struggling Business" archetype.
     * Characteristics: 60% on-time, declining orders, high utilization, overdue
     * Default risk: 50%
     */
    public static MerchantArchetype strugglingBusiness() {
        return MerchantArchetype.builder()
                .name("Struggling Business")
                .defaultProbability(0.50)
                .totalOrdersRange(new Range<>(15, 50))
                .orderFrequencyRange(new Range<>(0.8, 2.0))
                .avgOrderValueRange(new Range<>(BigDecimal.valueOf(12_000), BigDecimal.valueOf(35_000)))
                .orderTrendSlopeRange(new Range<>(-0.30, -0.05))
                .cancellationRateRange(new Range<>(0.08, 0.20))
                .returnRateRange(new Range<>(0.05, 0.15))
                .daysSinceLastOrderRange(new Range<>(10, 30))
                .uniqueSkusOrderedRange(new Range<>(10, 28))
                .topSkuConcentrationRange(new Range<>(0.35, 0.70))
                .onTimePaymentPctRange(new Range<>(0.50, 0.68))
                .avgDaysToPayRange(new Range<>(25.0, 45.0))
                .worstDaysToPayRange(new Range<>(50, 85))
                .partialPaymentFrequencyRange(new Range<>(0.20, 0.40))
                .consecutiveOnTimeStreakRange(new Range<>(0, 3))
                .totalOverdueAmountRange(new Range<>(BigDecimal.valueOf(20_000), BigDecimal.valueOf(100_000)))
                .currentCreditLimitRange(new Range<>(BigDecimal.valueOf(80_000), BigDecimal.valueOf(300_000)))
                .currentUtilizationRatioRange(new Range<>(0.70, 0.95))
                .peakUtilizationRatioRange(new Range<>(0.85, 1.00))
                .utilizationTrendSlopeRange(new Range<>(0.05, 0.30))
                .limitIncreaseCountRange(new Range<>(0, 1))
                .daysSinceLastLimitChangeRange(new Range<>(90, 450))
                .tenureDaysRange(new Range<>(120, 730))
                .preferredCategories(List.of("Kiosk", "General Store", "Grocery", "Hardware Store"))
                .preferredCities(List.of("Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret", "Thika", "Ruiru", "Machakos"))
                .verificationStatuses(List.of("VERIFIED", "PENDING", "UNVERIFIED"))
                .build();
    }

    /**
     * Create the "High Default Risk" archetype.
     * Characteristics: <50% on-time, severe overdue, very high utilization
     * Default risk: 75%
     */
    public static MerchantArchetype highDefaultRisk() {
        return MerchantArchetype.builder()
                .name("High Default Risk")
                .defaultProbability(0.75)
                .totalOrdersRange(new Range<>(5, 25))
                .orderFrequencyRange(new Range<>(0.5, 1.5))
                .avgOrderValueRange(new Range<>(BigDecimal.valueOf(8_000), BigDecimal.valueOf(25_000)))
                .orderTrendSlopeRange(new Range<>(-0.50, -0.10))
                .cancellationRateRange(new Range<>(0.15, 0.35))
                .returnRateRange(new Range<>(0.10, 0.25))
                .daysSinceLastOrderRange(new Range<>(15, 45))
                .uniqueSkusOrderedRange(new Range<>(5, 18))
                .topSkuConcentrationRange(new Range<>(0.45, 0.85))
                .onTimePaymentPctRange(new Range<>(0.20, 0.55))
                .avgDaysToPayRange(new Range<>(35.0, 60.0))
                .worstDaysToPayRange(new Range<>(70, 120))
                .partialPaymentFrequencyRange(new Range<>(0.30, 0.60))
                .consecutiveOnTimeStreakRange(new Range<>(0, 2))
                .totalOverdueAmountRange(new Range<>(BigDecimal.valueOf(50_000), BigDecimal.valueOf(200_000)))
                .currentCreditLimitRange(new Range<>(BigDecimal.valueOf(50_000), BigDecimal.valueOf(250_000)))
                .currentUtilizationRatioRange(new Range<>(0.85, 1.00))
                .peakUtilizationRatioRange(new Range<>(0.95, 1.00))
                .utilizationTrendSlopeRange(new Range<>(0.10, 0.50))
                .limitIncreaseCountRange(new Range<>(0, 1))
                .daysSinceLastLimitChangeRange(new Range<>(120, 730))
                .tenureDaysRange(new Range<>(60, 545))
                .preferredCategories(List.of("Kiosk", "General Store", "Grocery"))
                .preferredCities(List.of("Nairobi", "Mombasa", "Kisumu", "Nakuru", "Eldoret", "Thika", "Ruiru", "Machakos", "Nyeri"))
                .verificationStatuses(List.of("PENDING", "UNVERIFIED"))
                .build();
    }

    /**
     * Get all standard archetypes.
     */
    public static List<MerchantArchetype> allArchetypes() {
        return List.of(
                excellentRetailer(),
                goodHardwareStore(),
                averageShop(),
                riskyNewcomer(),
                strugglingBusiness(),
                highDefaultRisk()
        );
    }
}
