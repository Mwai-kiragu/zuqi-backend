package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticMerchant;
import com.zuqi.ai.synthetic.SyntheticOrder;
import com.zuqi.ai.synthetic.SyntheticPayment;
import com.zuqi.ai.synthetic.profiles.AnomalyPatterns;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates synthetic payment records for a complete order history.
 *
 * <p>Each order receives one of three payment outcomes:
 * <ol>
 *   <li><b>Full payment</b> — single record, {@code isPartial=false}, {@code isDefault=false}.</li>
 *   <li><b>Partial payments</b> — 2–3 records whose amounts sum exactly to the invoice total,
 *       all with {@code isPartial=true}.</li>
 *   <li><b>Missed payment (default)</b> — single record with {@code isDefault=true}, amount set
 *       to the invoice total (representing the outstanding obligation).</li>
 * </ol>
 *
 * <p><b>Payment timing</b> is sampled from the merchant's archetype distribution and then
 * adjusted by an {@link AnomalyPatterns.PaymentDistressPattern} for at-risk merchants:
 * <ul>
 *   <li>DEFAULTER → {@code MISSED_PAYMENTS}: after month 5 of history, default probability
 *       escalates by 30 percentage points per subsequent month.</li>
 *   <li>DECLINING_RISK → {@code DETERIORATING_TIMING} or {@code INCREASING_PARTIALS}:
 *       days-to-payment or partial-payment rate grow over time with no outright defaults.</li>
 *   <li>All others → no distress; archetype distribution applied directly.</li>
 * </ul>
 *
 * <p><b>Payment methods:</b> M-Pesa 65%, KCB_TRANSFER 25%, CASH 10%.
 *
 * <p>Expected volume: ~160,000 payments per 500-merchant distributor run.
 */
@Component
@Slf4j
public class PaymentBehaviorGenerator {

    /** RNG seed offset — separates this generator's stream from previous generators. */
    private static final long SEED_OFFSET = 3_000_000L;

    /** Payment method selection thresholds: MPESA < 0.65, KCB_TRANSFER < 0.90, CASH otherwise. */
    private static final double MPESA_THRESHOLD  = 0.65;
    private static final double BANK_THRESHOLD   = 0.90;

    /** Base partial-payment probabilities per archetype (before distress adjustments). */
    private static final Map<MerchantArchetype, Double> BASE_PARTIAL_PROB = Map.of(
            MerchantArchetype.STEADY_GROWER,      0.03,
            MerchantArchetype.STABLE_PERFORMER,   0.02,
            MerchantArchetype.INCONSISTENT_BUYER, 0.12,
            MerchantArchetype.NEW_ENTRANT,         0.08,
            MerchantArchetype.DECLINING_RISK,      0.15,
            MerchantArchetype.DEFAULTER,           0.20
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generate payments for every order in {@code orders}.
     *
     * @param orders    all synthetic orders (from {@link OrderHistoryGenerator})
     * @param merchants all synthetic merchants (used to look up archetype and syntheticId)
     * @param config    generation parameters (seed, historyMonths)
     * @return unmodifiable list of synthetic payment records
     */
    public List<SyntheticPayment> generate(List<SyntheticOrder> orders,
                                           List<SyntheticMerchant> merchants,
                                           SyntheticDataConfig config) {
        Random rng = new Random(config.randomSeed() + SEED_OFFSET);

        // Lookup maps
        Map<UUID, List<SyntheticOrder>> ordersByMerchant = orders.stream()
                .collect(Collectors.groupingBy(SyntheticOrder::merchantRef));

        LocalDate historyStart = LocalDate.now().minusMonths(config.historyMonths());

        List<SyntheticPayment> payments = new ArrayList<>();

        for (SyntheticMerchant merchant : merchants) {
            List<SyntheticOrder> merchantOrders =
                    ordersByMerchant.getOrDefault(merchant.syntheticId(), List.of());
            if (merchantOrders.isEmpty()) continue;

            generateForMerchant(merchant, merchantOrders, historyStart, rng, payments);
        }

        log.info("Generated {} payments for {} orders across {} merchants",
                payments.size(), orders.size(), merchants.size());

        return Collections.unmodifiableList(payments);
    }

    // -------------------------------------------------------------------------
    // Per-merchant payment generation
    // -------------------------------------------------------------------------

    private void generateForMerchant(SyntheticMerchant merchant,
                                     List<SyntheticOrder> merchantOrders,
                                     LocalDate historyStart,
                                     Random rng,
                                     List<SyntheticPayment> paymentsOut) {
        MerchantArchetype archetype = merchant.merchantArchetype();

        // Per-merchant decision: will this merchant eventually default?
        boolean willDefault = archetype.sampleDefaults(rng);

        // Select distress pattern (non-null only for DECLINING_RISK and DEFAULTER)
        AnomalyPatterns.PaymentDistressPattern distress = selectDistressPattern(archetype, rng);

        // Process orders in chronological order so distress escalates correctly
        List<SyntheticOrder> sorted = merchantOrders.stream()
                .sorted(Comparator.comparing(SyntheticOrder::orderDate))
                .toList();

        for (SyntheticOrder order : sorted) {
            long monthsFromStart = ChronoUnit.MONTHS.between(
                    historyStart, order.orderDate().toLocalDate());

            // Check for missed payment (only DEFAULTER with willDefault=true)
            if (willDefault && isDefaultCandidate(distress, monthsFromStart, rng)) {
                paymentsOut.add(missedPayment(order, merchant, rng));
                continue;
            }

            // Determine payment timing
            int baseDays     = archetype.samplePaymentDays(rng);
            int adjustedDays = applyTimingDistress(baseDays, distress, monthsFromStart);
            adjustedDays     = Math.max(1, adjustedDays);

            // Determine full vs partial
            double partialProb = getPartialProbability(archetype, distress, monthsFromStart);
            if (rng.nextDouble() < partialProb) {
                generatePartialPayments(order, merchant, adjustedDays, rng, paymentsOut);
            } else {
                paymentsOut.add(fullPayment(order, merchant, adjustedDays, rng));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Payment record builders
    // -------------------------------------------------------------------------

    /** Single full payment for an order. */
    private SyntheticPayment fullPayment(SyntheticOrder order, SyntheticMerchant merchant,
                                         int daysAfter, Random rng) {
        return new SyntheticPayment(
                new UUID(rng.nextLong(), rng.nextLong()),
                order.syntheticId(),
                merchant.syntheticId(),
                order.totalAmount(),
                order.orderDate().plusDays(daysAfter),
                samplePaymentMethod(rng),
                daysAfter,
                false,
                false);
    }

    /**
     * Missed payment record.
     * {@code amount} is set to the invoice total (representing outstanding obligation).
     * {@code daysAfterInvoice} is 90+ days to signal severely overdue status.
     */
    private SyntheticPayment missedPayment(SyntheticOrder order, SyntheticMerchant merchant,
                                           Random rng) {
        int daysOverdue = 90 + rng.nextInt(30);
        return new SyntheticPayment(
                new UUID(rng.nextLong(), rng.nextLong()),
                order.syntheticId(),
                merchant.syntheticId(),
                order.totalAmount(),
                order.orderDate().plusDays(daysOverdue),
                "NONE",
                daysOverdue,
                false,
                true);
    }

    /**
     * Generate 2–3 partial payments for one order.
     *
     * <p>Amounts are split proportionally using random fractions; the final payment
     * takes the exact remainder so the sum always equals the invoice total exactly.
     * All records carry {@code isPartial=true}.
     */
    private void generatePartialPayments(SyntheticOrder order, SyntheticMerchant merchant,
                                         int adjustedDays, Random rng,
                                         List<SyntheticPayment> paymentsOut) {
        int    numPayments = (rng.nextDouble() < 0.30) ? 3 : 2;
        String method      = samplePaymentMethod(rng);
        BigDecimal remaining = order.totalAmount();
        // Minimum to leave for subsequent payments so last is always positive
        BigDecimal minKeep = new BigDecimal("0.01");

        for (int p = 0; p < numPayments; p++) {
            boolean isLast = (p == numPayments - 1);
            BigDecimal amount;

            if (isLast) {
                amount = remaining;
            } else {
                // Take 30–65% of what remains, never leaving less than minKeep
                double fraction = 0.30 + rng.nextDouble() * 0.35;
                BigDecimal candidate = remaining.multiply(BigDecimal.valueOf(fraction))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal maxAllowed = remaining.subtract(minKeep);
                amount = candidate.min(maxAllowed).max(minKeep);
            }

            remaining = remaining.subtract(amount);

            // Spread payment dates: first at ~50%, second at ~100%, third beyond
            int days = switch (p) {
                case 0  -> Math.max(1, adjustedDays / 2 + rng.nextInt(3));
                case 1  -> adjustedDays + rng.nextInt(5);
                default -> adjustedDays + 14 + rng.nextInt(7);
            };

            paymentsOut.add(new SyntheticPayment(
                    new UUID(rng.nextLong(), rng.nextLong()),
                    order.syntheticId(),
                    merchant.syntheticId(),
                    amount,
                    order.orderDate().plusDays(days),
                    method,
                    days,
                    true,
                    false));
        }
    }

    // -------------------------------------------------------------------------
    // Distress logic
    // -------------------------------------------------------------------------

    /**
     * Select a {@link AnomalyPatterns.PaymentDistressPattern} appropriate to the archetype.
     * Returns {@code null} for healthy archetypes.
     */
    private AnomalyPatterns.PaymentDistressPattern selectDistressPattern(
            MerchantArchetype archetype, Random rng) {
        return switch (archetype) {
            case DEFAULTER       -> AnomalyPatterns.PaymentDistressPattern.MISSED_PAYMENTS;
            case DECLINING_RISK  -> rng.nextBoolean()
                    ? AnomalyPatterns.PaymentDistressPattern.DETERIORATING_TIMING
                    : AnomalyPatterns.PaymentDistressPattern.INCREASING_PARTIALS;
            default              -> null;
        };
    }

    /**
     * Returns true when this order should be treated as a missed payment.
     * Only applies to {@link AnomalyPatterns.PaymentDistressPattern#MISSED_PAYMENTS}.
     */
    private boolean isDefaultCandidate(AnomalyPatterns.PaymentDistressPattern distress,
                                       long monthsFromStart, Random rng) {
        if (distress != AnomalyPatterns.PaymentDistressPattern.MISSED_PAYMENTS) return false;
        if (monthsFromStart < distress.activationMonth) return false;

        long   monthsActive = monthsFromStart - distress.activationMonth;
        double defaultProb  = Math.min(0.90, monthsActive * distress.intensityGrowth);
        return rng.nextDouble() < defaultProb;
    }

    /**
     * Adjust base payment days upward for {@link AnomalyPatterns.PaymentDistressPattern#DETERIORATING_TIMING}.
     * Days multiply by {@code (1 + monthsActive × intensityGrowth)} after activation.
     */
    private int applyTimingDistress(int baseDays,
                                    AnomalyPatterns.PaymentDistressPattern distress,
                                    long monthsFromStart) {
        if (distress != AnomalyPatterns.PaymentDistressPattern.DETERIORATING_TIMING) return baseDays;
        if (monthsFromStart < distress.activationMonth) return baseDays;

        long   monthsActive = monthsFromStart - distress.activationMonth;
        double multiplier   = 1.0 + monthsActive * distress.intensityGrowth;
        return (int) Math.round(baseDays * multiplier);
    }

    /**
     * Compute per-invoice partial-payment probability.
     * Base probability from archetype, amplified by
     * {@link AnomalyPatterns.PaymentDistressPattern#INCREASING_PARTIALS} distress.
     * Capped at 0.70 so at least some orders receive full payment.
     */
    private double getPartialProbability(MerchantArchetype archetype,
                                         AnomalyPatterns.PaymentDistressPattern distress,
                                         long monthsFromStart) {
        double prob = BASE_PARTIAL_PROB.getOrDefault(archetype, 0.05);

        if (distress == AnomalyPatterns.PaymentDistressPattern.INCREASING_PARTIALS
                && monthsFromStart >= distress.activationMonth) {
            long monthsActive = monthsFromStart - distress.activationMonth;
            prob += monthsActive * distress.intensityGrowth;
        }

        return Math.min(0.70, prob);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private String samplePaymentMethod(Random rng) {
        double roll = rng.nextDouble();
        if (roll < MPESA_THRESHOLD) return "MPESA";
        if (roll < BANK_THRESHOLD)  return "KCB_TRANSFER";
        return "CASH";
    }
}
