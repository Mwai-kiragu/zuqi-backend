package com.zuqi.ai.synthetic.profiles;

import java.util.Random;

/**
 * Behavioural archetypes that drive realistic synthetic data generation.
 *
 * Each constant encodes the statistical distributions used by all generators:
 * order frequency, order value, payment timeliness, monthly growth trend, and
 * default probability.
 *
 * Population distribution (default, configurable via SyntheticDataConfig):
 * STEADY_GROWER 35% | STABLE_PERFORMER 25% | INCONSISTENT_BUYER 20%
 * NEW_ENTRANT 10%   | DECLINING_RISK 7%    | DEFAULTER 3%
 */
public enum MerchantArchetype {

    /**
     * Reliable, growing merchant. Consistent ordering cadence, pays on time.
     * Credit grade typically A or B.
     */
    STEADY_GROWER(
            0.35,
            2.5,  0.5,        // orders/week: mean, stdDev
            25_000, 8_000,    // order value KES: mean, stdDev
            7,    3,          // payment days: mean, stdDev
            0.02,             // monthly growth rate
            0.02              // default probability
    ),

    /**
     * Flat-volume, low-risk merchant. Regular but not growing.
     * Credit grade typically B.
     */
    STABLE_PERFORMER(
            0.25,
            2.0,  0.3,
            20_000, 5_000,
            5,    2,
            0.005,
            0.01
    ),

    /**
     * Erratic ordering frequency, occasionally late on payments.
     * Credit grade typically C.
     */
    INCONSISTENT_BUYER(
            0.20,
            1.2,  1.0,
            15_000, 10_000,
            14,   7,
            0.0,
            0.08
    ),

    /**
     * Short history, small orders, building creditworthiness.
     * Credit grade typically C or D (insufficient history).
     */
    NEW_ENTRANT(
            0.10,
            0.8,  0.4,
            8_000, 4_000,
            10,   5,
            0.05,
            0.05
    ),

    /**
     * Deteriorating payment behaviour, shrinking order volume.
     * Credit grade typically D — requires close monitoring.
     */
    DECLINING_RISK(
            0.07,
            1.5,  0.8,
            18_000, 6_000,
            21,   10,
            -0.03,
            0.20
    ),

    /**
     * High default risk: missed payments, irregular ordering.
     * Credit grade F — should not receive new credit.
     */
    DEFAULTER(
            0.03,
            1.0,  0.8,
            12_000, 8_000,
            30,   15,
            -0.05,
            0.80
    );

    // -------------------------------------------------------------------------
    // Distribution parameters
    // -------------------------------------------------------------------------

    /** Fraction of merchants assigned this archetype in the default population. */
    public final double populationRatio;

    /** Mean number of orders placed per week. */
    public final double ordersPerWeekMean;
    /** Standard deviation of weekly order frequency. */
    public final double ordersPerWeekStdDev;

    /** Mean order value in KES. */
    public final double orderValueMeanKes;
    /** Standard deviation of order value in KES. */
    public final double orderValueStdDevKes;

    /** Mean days from invoice to payment. */
    public final double paymentDaysMean;
    /** Standard deviation of days to payment. */
    public final double paymentDaysStdDev;

    /** Monthly compounding growth rate applied to order value over the history window.
     *  Positive = growing, negative = declining. */
    public final double monthlyGrowthRate;

    /** Probability (0–1) that a merchant of this archetype eventually defaults. */
    public final double defaultProbability;

    MerchantArchetype(
            double populationRatio,
            double ordersPerWeekMean, double ordersPerWeekStdDev,
            double orderValueMeanKes, double orderValueStdDevKes,
            double paymentDaysMean, double paymentDaysStdDev,
            double monthlyGrowthRate,
            double defaultProbability) {
        this.populationRatio       = populationRatio;
        this.ordersPerWeekMean     = ordersPerWeekMean;
        this.ordersPerWeekStdDev   = ordersPerWeekStdDev;
        this.orderValueMeanKes     = orderValueMeanKes;
        this.orderValueStdDevKes   = orderValueStdDevKes;
        this.paymentDaysMean       = paymentDaysMean;
        this.paymentDaysStdDev     = paymentDaysStdDev;
        this.monthlyGrowthRate     = monthlyGrowthRate;
        this.defaultProbability    = defaultProbability;
    }

    // -------------------------------------------------------------------------
    // Sampling helpers — draw a single value from this archetype's distribution
    // -------------------------------------------------------------------------

    /**
     * Sample a weekly order count. Always &ge; 0.
     */
    public double sampleOrdersPerWeek(Random rng) {
        return Math.max(0, ordersPerWeekMean + rng.nextGaussian() * ordersPerWeekStdDev);
    }

    /**
     * Sample an order value in KES. Clamped to a minimum of 500 KES.
     */
    public double sampleOrderValueKes(Random rng) {
        return Math.max(500, orderValueMeanKes + rng.nextGaussian() * orderValueStdDevKes);
    }

    /**
     * Sample days-to-payment. Always &ge; 0.
     */
    public int samplePaymentDays(Random rng) {
        return (int) Math.max(0, paymentDaysMean + rng.nextGaussian() * paymentDaysStdDev);
    }

    /**
     * Determine if a merchant of this archetype defaults, using the given RNG.
     */
    public boolean sampleDefaults(Random rng) {
        return rng.nextDouble() < defaultProbability;
    }

    /**
     * Apply monthly growth/decline to a base order value after {@code monthsElapsed}.
     */
    public double applyGrowth(double baseValue, int monthsElapsed) {
        return baseValue * Math.pow(1 + monthlyGrowthRate, monthsElapsed);
    }
}
