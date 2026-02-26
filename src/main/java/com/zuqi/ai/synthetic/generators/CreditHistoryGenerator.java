package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticCreditEvaluation;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticMerchant;
import com.zuqi.ai.synthetic.SyntheticPayment;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates synthetic credit evaluation records for every merchant across a history window.
 *
 * <p>One evaluation is produced per merchant per month, providing labelled training data for the
 * credit risk classifier. The {@code defaulted} flag is the ground-truth label; the
 * {@code daysToDefault} field supports survival analysis (time-to-event modelling).
 *
 * <h3>Grade progression by archetype</h3>
 * <table border="1">
 * <tr><th>Archetype</th><th>Months 0-2</th><th>Months 3-5</th><th>Months 6-8</th><th>Months 9+</th></tr>
 * <tr><td>STEADY_GROWER</td><td>B</td><td>A</td><td>A</td><td>A</td></tr>
 * <tr><td>STABLE_PERFORMER</td><td colspan="4">B (constant)</td></tr>
 * <tr><td>INCONSISTENT_BUYER</td><td colspan="4">C (±1 noise)</td></tr>
 * <tr><td>NEW_ENTRANT</td><td>D</td><td>C</td><td>B</td><td>B</td></tr>
 * <tr><td>DECLINING_RISK</td><td>B</td><td>C</td><td>D</td><td>D</td></tr>
 * <tr><td>DEFAULTER</td><td>B</td><td>C</td><td>D</td><td>F</td></tr>
 * </table>
 *
 * <p><b>Payment quality modifiers:</b>
 * <ul>
 *   <li>Cumulative missed payments ≥ 1 → cap grade at D</li>
 *   <li>Cumulative missed payments ≥ 3 → force grade F</li>
 *   <li>Partial payment ratio &gt; 0.4 → degrade by 1</li>
 * </ul>
 *
 * <p><b>Credit limit trajectory:</b> limits compound monthly at archetype-specific rates
 * (STEADY_GROWER +3%, DECLINING_RISK −4%, DEFAULTER −10%) clamped to [40%, 300%] of initial.
 *
 * <p><b>Default events:</b> merchants whose {@link MerchantArchetype#sampleDefaults} returns
 * {@code true} are assigned a random default date (month 6-11). Evaluations after that date are
 * not generated. All pre-default evaluations carry {@code defaulted=true} and a positive
 * {@code daysToDefault} value, enabling survival-analysis training.
 *
 * <p>Expected volume: ~6,000 evaluations per 500-merchant / 12-month run.
 */
@Component
@Slf4j
public class CreditHistoryGenerator {

    /** RNG seed offset — keeps this generator's stream separate from all others. */
    private static final long SEED_OFFSET = 6_000_000L;

    /**
     * Grade label lookup by integer value (index = grade value 0–4).
     * 0 = F, 1 = D, 2 = C, 3 = B, 4 = A.
     */
    private static final String[] GRADE_LABELS = {"F", "D", "C", "B", "A"};

    /**
     * Monthly credit limit growth/decay multipliers per archetype.
     * Compounded over {@code monthsFromStart} via {@code Math.pow(rate, months)}.
     */
    private static final Map<MerchantArchetype, Double> MONTHLY_LIMIT_RATE = Map.of(
            MerchantArchetype.STEADY_GROWER,      1.030,
            MerchantArchetype.STABLE_PERFORMER,   1.005,
            MerchantArchetype.INCONSISTENT_BUYER, 1.000,
            MerchantArchetype.NEW_ENTRANT,         1.020,
            MerchantArchetype.DECLINING_RISK,      0.960,
            MerchantArchetype.DEFAULTER,           0.900
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generate monthly credit evaluations for every merchant in the history window.
     *
     * @param merchants all synthetic merchants (archetype and initial credit limit used)
     * @param payments  all synthetic payments (used to derive payment-quality grade modifiers)
     * @param config    generation parameters — seed and historyMonths
     * @return unmodifiable list of credit evaluation DTOs
     */
    public List<SyntheticCreditEvaluation> generate(List<SyntheticMerchant> merchants,
                                                    List<SyntheticPayment> payments,
                                                    SyntheticDataConfig config) {
        if (merchants.isEmpty()) {
            return Collections.emptyList();
        }

        Random rng = new Random(config.randomSeed() + SEED_OFFSET);

        LocalDate historyStart = LocalDate.now().minusMonths(config.historyMonths());
        LocalDate historyEnd   = LocalDate.now();

        // Payment quality maps: one entry per merchant
        Map<UUID, Map<Integer, Integer>> defaultsByMerchantMonth =
                buildDefaultsByMonth(payments, historyStart);
        Map<UUID, Double> partialRates = buildPartialRates(payments);

        List<SyntheticCreditEvaluation> evals = new ArrayList<>();

        for (SyntheticMerchant merchant : merchants) {
            boolean willDefault = merchant.merchantArchetype().sampleDefaults(rng);
            LocalDate defaultDate = null;
            if (willDefault) {
                // Default occurs in months 6-11 (matches PaymentBehaviorGenerator's activation)
                int defaultMonth = 6 + rng.nextInt(Math.max(1, config.historyMonths() - 6));
                defaultDate = historyStart.plusMonths(defaultMonth)
                        .plusDays(rng.nextInt(28));
                if (defaultDate.isAfter(historyEnd)) defaultDate = historyEnd.minusDays(1);
            }

            double partialRate = partialRates.getOrDefault(merchant.syntheticId(), 0.0);
            Map<Integer, Integer> defaultsByMonth =
                    defaultsByMerchantMonth.getOrDefault(merchant.syntheticId(), Map.of());

            generateForMerchant(merchant, historyStart, historyEnd, config.historyMonths(),
                    willDefault, defaultDate, defaultsByMonth, partialRate, rng, evals);
        }

        log.info("Generated {} credit evaluations for {} merchants (seed={})",
                evals.size(), merchants.size(), config.randomSeed());

        return Collections.unmodifiableList(evals);
    }

    // -------------------------------------------------------------------------
    // Per-merchant evaluation generation
    // -------------------------------------------------------------------------

    private void generateForMerchant(SyntheticMerchant merchant,
                                     LocalDate historyStart, LocalDate historyEnd,
                                     int historyMonths,
                                     boolean willDefault, LocalDate defaultDate,
                                     Map<Integer, Integer> defaultsByMonth,
                                     double partialRate, Random rng,
                                     List<SyntheticCreditEvaluation> out) {
        BigDecimal initialLimit = merchant.initialCreditLimit();
        int cumulativeDefaults  = 0;

        for (int month = 0; month < historyMonths; month++) {
            // Evaluation date: a random day in the given calendar month
            LocalDate evalDate = historyStart.plusMonths(month)
                    .plusDays(1 + rng.nextInt(25));

            // Stop generating if evaluation falls after the default event
            if (willDefault && defaultDate != null && !evalDate.isBefore(defaultDate)) break;
            // Stop generating if evaluation falls outside the history window
            if (evalDate.isAfter(historyEnd)) break;

            // Accumulate missed payments through this month
            cumulativeDefaults += defaultsByMonth.getOrDefault(month, 0);

            int gradeValue = computeGradeValue(
                    merchant.merchantArchetype(), month, cumulativeDefaults, partialRate, rng);
            BigDecimal creditLimit = computeCreditLimit(
                    initialLimit, merchant.merchantArchetype(), month);

            Integer daysToDefault = null;
            if (willDefault && defaultDate != null) {
                long days = ChronoUnit.DAYS.between(evalDate, defaultDate);
                daysToDefault = (int) Math.max(0, days);
            }

            out.add(new SyntheticCreditEvaluation(
                    new UUID(rng.nextLong(), rng.nextLong()),
                    merchant.syntheticId(),
                    evalDate,
                    GRADE_LABELS[gradeValue],
                    creditLimit,
                    willDefault,
                    daysToDefault));
        }
    }

    // -------------------------------------------------------------------------
    // Grade computation
    // -------------------------------------------------------------------------

    /**
     * Compute integer grade value (0=F … 4=A) from archetype progression plus payment signals.
     *
     * <p>Noise of ±1 is applied (15% probability each direction) for STEADY_GROWER,
     * STABLE_PERFORMER, and NEW_ENTRANT to produce realistic grade variability.
     */
    private int computeGradeValue(MerchantArchetype archetype, int monthsFromStart,
                                  int cumulativeDefaults, double partialRate, Random rng) {
        // Base grade from archetype trajectory
        int grade = switch (archetype) {
            case STEADY_GROWER      -> monthsFromStart < 3 ? 3 : 4;            // B → A
            case STABLE_PERFORMER   -> 3;                                        // B
            case INCONSISTENT_BUYER -> 2;                                        // C
            case NEW_ENTRANT        -> monthsFromStart < 3 ? 1                   // D
                                    : monthsFromStart < 6 ? 2 : 3;              // C → B
            case DECLINING_RISK     -> monthsFromStart < 4 ? 3                   // B
                                    : monthsFromStart < 8 ? 2 : 1;              // C → D
            case DEFAULTER          -> monthsFromStart < 3 ? 3                   // B
                                    : monthsFromStart < 6 ? 2                    // C
                                    : monthsFromStart < 9 ? 1 : 0;              // D → F
        };

        // Payment quality modifiers
        if (cumulativeDefaults >= 3)      grade = 0;                  // multiple missed → F
        else if (cumulativeDefaults >= 1) grade = Math.min(grade, 1); // any missed → cap at D

        if (partialRate > 0.4) grade = Math.max(0, grade - 1);

        // Small random noise for stable archetypes only (±1 grade, 15% each direction)
        if (archetype == MerchantArchetype.STEADY_GROWER
                || archetype == MerchantArchetype.STABLE_PERFORMER
                || archetype == MerchantArchetype.NEW_ENTRANT) {
            double roll = rng.nextDouble();
            if      (roll < 0.15) grade = Math.min(4, grade + 1);
            else if (roll < 0.30) grade = Math.max(0, grade - 1);
        }

        return Math.max(0, Math.min(4, grade));
    }

    // -------------------------------------------------------------------------
    // Credit limit trajectory
    // -------------------------------------------------------------------------

    /**
     * Compute the credit limit at a given month from the initial value.
     * Limit is clamped to [40%, 300%] of the initial to prevent extreme values.
     */
    private BigDecimal computeCreditLimit(BigDecimal initialLimit,
                                          MerchantArchetype archetype, int month) {
        double rate       = MONTHLY_LIMIT_RATE.getOrDefault(archetype, 1.0);
        double multiplier = Math.pow(rate, month);
        multiplier = Math.max(0.40, Math.min(3.0, multiplier));
        return initialLimit.multiply(BigDecimal.valueOf(multiplier))
                .setScale(0, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Payment statistics
    // -------------------------------------------------------------------------

    /**
     * Build a map of {@code merchantId → (month → count)} for missed-payment records
     * ({@link SyntheticPayment#isDefault()} = true).
     */
    private Map<UUID, Map<Integer, Integer>> buildDefaultsByMonth(
            List<SyntheticPayment> payments, LocalDate historyStart) {
        Map<UUID, Map<Integer, Integer>> result = new HashMap<>();
        for (SyntheticPayment payment : payments) {
            if (!payment.isDefault()) continue;
            int month = (int) ChronoUnit.MONTHS.between(
                    historyStart, payment.paymentDate().toLocalDate());
            result.computeIfAbsent(payment.merchantRef(), k -> new HashMap<>())
                    .merge(month, 1, Integer::sum);
        }
        return result;
    }

    /**
     * Build a map of {@code merchantId → partialPaymentRate} (fraction of payments that are partial).
     */
    private Map<UUID, Double> buildPartialRates(List<SyntheticPayment> payments) {
        if (payments.isEmpty()) return Map.of();
        Map<UUID, Long> total   = payments.stream()
                .collect(Collectors.groupingBy(SyntheticPayment::merchantRef, Collectors.counting()));
        Map<UUID, Long> partial = payments.stream()
                .filter(SyntheticPayment::isPartial)
                .collect(Collectors.groupingBy(SyntheticPayment::merchantRef, Collectors.counting()));
        Map<UUID, Double> rates = new HashMap<>();
        total.forEach((id, t) -> {
            long p = partial.getOrDefault(id, 0L);
            rates.put(id, (double) p / t);
        });
        return rates;
    }
}
