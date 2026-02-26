package com.zuqi.ai.synthetic.profiles;

/**
 * Labeled anomaly signatures injected into synthetic data during generation.
 *
 * Each nested enum defines the distinct patterns a generator can inject.
 * Pattern parameters drive generator behaviour — probability of injection,
 * characteristics of the anomalous signal, and the label stored on the DTO
 * (e.g. {@code SyntheticInventoryMovement#shrinkagePattern()}).
 *
 * Used by:
 * <ul>
 *   <li>{@code InventoryMovementGenerator} — shrinkage patterns</li>
 *   <li>{@code PaymentBehaviorGenerator}   — payment distress patterns</li>
 *   <li>{@code OrderHistoryGenerator}      — data quality patterns</li>
 * </ul>
 */
public final class AnomalyPatterns {

    private AnomalyPatterns() {}

    // =========================================================================
    // Inventory Shrinkage
    // =========================================================================

    /**
     * Patterns of inventory shrinkage injected into stock movement generation.
     *
     * <p>Each pattern has:
     * <ul>
     *   <li>{@code injectionRate}   — fraction of warehouses that receive this pattern</li>
     *   <li>{@code quantityFactor}  — multiplier on "normal" ADJUSTMENT quantity</li>
     *   <li>{@code burstDays}       — how many consecutive days the anomaly spans</li>
     * </ul>
     */
    public enum ShrinkagePattern {

        /**
         * Slow, steady loss accumulating over weeks.
         * Hard to detect from a single snapshot — requires trend analysis.
         * Injection rate: 3% of warehouses per month.
         */
        GRADUAL(0.03, 1.5, 30),

        /**
         * Large single-event loss — theft, spoilage, or miscount.
         * High magnitude, concentrated in 1–3 days.
         * Injection rate: 1% of warehouses per month.
         */
        SUDDEN(0.01, 5.0, 2),

        /**
         * Losses tied to a specific user (employee theft pattern).
         * Adjustments always recorded by the same userId.
         * Injection rate: 2% of warehouses per month.
         */
        CONCENTRATED_USER(0.02, 2.0, 14),

        /**
         * Losses clustered in a specific time window (e.g. weekends/nights).
         * Injection rate: 2% of warehouses per month.
         */
        CONCENTRATED_TIME(0.02, 2.5, 7);

        /** Fraction of warehouses that receive this shrinkage pattern per month. */
        public final double injectionRate;
        /** Multiplier applied to the normal ADJUSTMENT magnitude. */
        public final double quantityFactor;
        /** Duration of the anomaly in days. */
        public final int    burstDays;

        ShrinkagePattern(double injectionRate, double quantityFactor, int burstDays) {
            this.injectionRate  = injectionRate;
            this.quantityFactor = quantityFactor;
            this.burstDays      = burstDays;
        }
    }

    // =========================================================================
    // Payment Distress
    // =========================================================================

    /**
     * Patterns of payment distress injected into payment generation for
     * DECLINING_RISK and DEFAULTER archetype merchants.
     *
     * <p>Each pattern has:
     * <ul>
     *   <li>{@code activationMonth} — how many months into the history window
     *       the distress signal begins appearing</li>
     *   <li>{@code intensityGrowth} — rate at which the distress worsens per month</li>
     * </ul>
     */
    public enum PaymentDistressPattern {

        /**
         * Payment timing gradually worsens — on-time → late → very late.
         * Days-to-payment increase by {@code intensityGrowth}% per month.
         */
        DETERIORATING_TIMING(3, 0.20),

        /**
         * Increasing proportion of invoices paid partially rather than in full.
         * Partial payment rate grows by {@code intensityGrowth} percentage points/month.
         */
        INCREASING_PARTIALS(4, 0.10),

        /**
         * Payments start being missed entirely — isDefault flag set.
         * Starts after {@code activationMonth} months, then escalates rapidly.
         */
        MISSED_PAYMENTS(5, 0.30);

        /** Month in the history window at which this distress pattern starts. */
        public final int    activationMonth;
        /** Rate at which distress intensifies per subsequent month. */
        public final double intensityGrowth;

        PaymentDistressPattern(int activationMonth, double intensityGrowth) {
            this.activationMonth = activationMonth;
            this.intensityGrowth = intensityGrowth;
        }
    }

    // =========================================================================
    // Data Quality
    // =========================================================================

    /**
     * Data quality anomalies injected into order and merchant generation.
     * Used to train the data quality detection model.
     *
     * <p>Each pattern has:
     * <ul>
     *   <li>{@code injectionRate} — fraction of records that receive this anomaly</li>
     * </ul>
     */
    public enum DataQualityPattern {

        /**
         * Order quantity is implausibly large (e.g. 10,000 units of a slow-moving SKU).
         * quantity &gt; mean × 20.
         * Injection rate: 0.5% of order items.
         */
        EXTREME_QUANTITY(0.005),

        /**
         * GPS coordinates fall outside Kenya's bounding box or in the ocean.
         * Latitude outside [-5.0, 5.0], longitude outside [33.9, 42.0].
         * Injection rate: 0.3% of merchant profiles.
         */
        COORDINATE_MISMATCH(0.003),

        /**
         * Two orders for the same merchant within 60 seconds with identical items.
         * Simulates duplicate submission from a flaky mobile client.
         * Injection rate: 0.8% of orders.
         */
        DUPLICATE_ORDER(0.008);

        /** Fraction of the relevant records that receive this anomaly. */
        public final double injectionRate;

        DataQualityPattern(double injectionRate) {
            this.injectionRate = injectionRate;
        }
    }
}
