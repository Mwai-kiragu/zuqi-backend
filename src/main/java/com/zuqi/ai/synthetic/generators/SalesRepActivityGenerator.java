package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import com.zuqi.ai.synthetic.dto.SyntheticRepActivity;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Generates synthetic sales rep visit activity records across a 12-month history window.
 *
 * <p>Ten sales reps share the merchant territory via a stable hash assignment (matching
 * {@link OrderHistoryGenerator}). Each rep visits their territory Monday–Saturday using a
 * round-robin schedule — 8–15 merchants per day, clamped to territory size.
 *
 * <p><b>Underperformance injection:</b> 10% of reps begin showing declining patterns after
 * month 3 of the history window:
 * <ul>
 *   <li><b>Shrinking visit counts</b> — daily visits decay by 7% per month (floor: 40% of base)</li>
 *   <li><b>Dropping conversion rates</b> — order conversion falls 6 pp/month (floor: 20%)</li>
 *   <li><b>Shorter visit durations</b> — duration ceiling shrinks from 25 min to 8 min over time</li>
 * </ul>
 *
 * <p>All activities generated for an underperforming rep carry {@code isUnderperforming=true},
 * providing the ground-truth label for the sales rep underperformance classifier.
 *
 * <p><b>Conversion rates by archetype</b> (before underperformance penalty):
 * STEADY_GROWER 80%, STABLE_PERFORMER 75%, INCONSISTENT_BUYER 57%,
 * NEW_ENTRANT 62%, DECLINING_RISK 52%, DEFAULTER 42%.
 *
 * <p>Expected volume: ~50,000 activity records per 500-merchant / 12-month run.
 */
@Component
@Slf4j
public class SalesRepActivityGenerator {

    /** RNG seed offset — keeps this generator's stream separate from all others. */
    private static final long   SEED_OFFSET          = 5_000_000L;

    /** Matches {@link OrderHistoryGenerator#N_REPS} — same rep pool shared across generators. */
    private static final int    N_REPS               = 10;

    /** Minimum and maximum merchant visits per working day per rep. */
    private static final int    VISITS_PER_DAY_MIN   = 8;
    private static final int    VISITS_PER_DAY_MAX   = 15;

    /** Fraction of reps that exhibit underperformance patterns. */
    private static final double UNDERPERFORMANCE_RATE = 0.10;

    /** Month (from history start) at which underperformance signals begin appearing. */
    private static final int    ACTIVATION_MONTH     = 3;

    /** Normal visit duration range: 15–45 minutes inclusive. */
    private static final int    NORMAL_DURATION_MIN  = 15;
    private static final int    NORMAL_DURATION_RANGE = 31; // nextInt(31) → 0..30 → 15..45

    /**
     * Base order conversion probability per archetype.
     * Represents the fraction of visits that result in an order under normal conditions.
     */
    private static final Map<MerchantArchetype, Double> BASE_CONVERSION = Map.of(
            MerchantArchetype.STEADY_GROWER,      0.80,
            MerchantArchetype.STABLE_PERFORMER,   0.75,
            MerchantArchetype.INCONSISTENT_BUYER, 0.57,
            MerchantArchetype.NEW_ENTRANT,         0.62,
            MerchantArchetype.DECLINING_RISK,      0.52,
            MerchantArchetype.DEFAULTER,           0.42
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generate visit activity records for all sales reps across the history window.
     *
     * @param merchants all synthetic merchants (territory assignment uses their syntheticId)
     * @param config    generation parameters — seed and historyMonths
     * @return unmodifiable list of synthetic rep activity records
     */
    public List<SyntheticRepActivity> generate(List<SyntheticMerchant> merchants,
                                               SyntheticDataConfig config) {
        if (merchants.isEmpty()) {
            return Collections.emptyList();
        }

        Random rng = new Random(config.randomSeed() + SEED_OFFSET);

        LocalDate historyStart = LocalDate.now().minusMonths(config.historyMonths());
        LocalDate historyEnd   = LocalDate.now();

        List<UUID> repIds = buildRepPool(config.randomSeed());
        Map<UUID, List<SyntheticMerchant>> byRep = assignMerchantsToReps(merchants, repIds);
        Set<UUID> underperformers = selectUnderperformers(repIds, rng);

        List<SyntheticRepActivity> activities = new ArrayList<>();

        for (UUID repId : repIds) {
            List<SyntheticMerchant> territory = byRep.getOrDefault(repId, List.of());
            if (territory.isEmpty()) continue;

            generateForRep(repId, territory, underperformers.contains(repId),
                    historyStart, historyEnd, rng, activities);
        }

        log.info("Generated {} rep activity records for {} reps across {} merchants (seed={})",
                activities.size(), repIds.size(), merchants.size(), config.randomSeed());

        return Collections.unmodifiableList(activities);
    }

    // -------------------------------------------------------------------------
    // Per-rep activity generation
    // -------------------------------------------------------------------------

    private void generateForRep(UUID repId, List<SyntheticMerchant> territory,
                                 boolean isUnderperforming,
                                 LocalDate historyStart, LocalDate historyEnd,
                                 Random rng, List<SyntheticRepActivity> out) {
        int territoryIdx = 0; // round-robin pointer through territory

        LocalDate date = historyStart;
        while (!date.isAfter(historyEnd)) {
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                date = date.plusDays(1);
                continue;
            }

            long monthsFromStart = ChronoUnit.MONTHS.between(historyStart, date);

            int baseVisits   = VISITS_PER_DAY_MIN
                    + rng.nextInt(VISITS_PER_DAY_MAX - VISITS_PER_DAY_MIN + 1);
            int visitsToday  = computeVisitCount(baseVisits, isUnderperforming, monthsFromStart);
            visitsToday = Math.min(visitsToday, territory.size());

            for (int v = 0; v < visitsToday; v++) {
                SyntheticMerchant merchant = territory.get(territoryIdx % territory.size());
                territoryIdx++;

                double conversion = effectiveConversion(
                        merchant.merchantArchetype(), isUnderperforming, monthsFromStart);
                boolean orderPlaced = rng.nextDouble() < conversion;

                BigDecimal orderValue = orderPlaced
                        ? BigDecimal.valueOf(
                                Math.max(500, merchant.merchantArchetype().sampleOrderValueKes(rng)))
                                .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                int duration = sampleDuration(isUnderperforming, monthsFromStart, rng);

                out.add(new SyntheticRepActivity(
                        new UUID(rng.nextLong(), rng.nextLong()),
                        repId,
                        merchant.syntheticId(),
                        date,
                        orderPlaced,
                        orderValue,
                        duration,
                        isUnderperforming));
            }

            date = date.plusDays(1);
        }
    }

    // -------------------------------------------------------------------------
    // Underperformance degradation helpers
    // -------------------------------------------------------------------------

    /**
     * Compute the effective visits-per-day after any underperformance decay.
     * Decays at 7% per active month, floored at 40% of the base count.
     */
    private int computeVisitCount(int base, boolean isUnderperforming, long monthsFromStart) {
        if (!isUnderperforming || monthsFromStart < ACTIVATION_MONTH) return base;
        long   active     = monthsFromStart - ACTIVATION_MONTH;
        double multiplier = Math.max(0.40, 1.0 - active * 0.07);
        return Math.max(1, (int) Math.round(base * multiplier));
    }

    /**
     * Compute the effective order conversion rate after any underperformance penalty.
     * Drops at 6 percentage points per active month, floored at 20%.
     */
    private double effectiveConversion(MerchantArchetype archetype,
                                       boolean isUnderperforming, long monthsFromStart) {
        double base = BASE_CONVERSION.getOrDefault(archetype, 0.65);
        if (!isUnderperforming || monthsFromStart < ACTIVATION_MONTH) return base;
        long active = monthsFromStart - ACTIVATION_MONTH;
        return Math.max(0.20, base - active * 0.06);
    }

    /**
     * Sample visit duration in minutes.
     * Normal reps: 15–45 minutes.
     * Underperforming reps: ceiling shrinks from 25 min at activation down to 8 min,
     * producing noticeably shorter visits as a disengagement signal.
     */
    private int sampleDuration(boolean isUnderperforming, long monthsFromStart, Random rng) {
        if (!isUnderperforming || monthsFromStart < ACTIVATION_MONTH) {
            return NORMAL_DURATION_MIN + rng.nextInt(NORMAL_DURATION_RANGE);
        }
        long active   = monthsFromStart - ACTIVATION_MONTH;
        int  ceiling  = Math.max(8, 25 - (int) (active * 2));
        return 5 + rng.nextInt(Math.max(1, ceiling - 5));
    }

    // -------------------------------------------------------------------------
    // Pool builders
    // -------------------------------------------------------------------------

    /**
     * Build rep UUID pool using a stable formula.
     * Matches the rep pool in {@link OrderHistoryGenerator} so the same logical
     * rep IDs appear in both order history and activity records.
     */
    private List<UUID> buildRepPool(long seed) {
        List<UUID> pool = new ArrayList<>(N_REPS);
        for (int i = 0; i < N_REPS; i++) {
            pool.add(new UUID(seed + 8_000_000L + i, seed * 13L + i));
        }
        return pool;
    }

    /**
     * Assign each merchant to a rep via stable hash, mirroring the assignment in
     * {@link OrderHistoryGenerator}. Each rep gets roughly equal territory size.
     */
    private Map<UUID, List<SyntheticMerchant>> assignMerchantsToReps(
            List<SyntheticMerchant> merchants, List<UUID> repIds) {
        Map<UUID, List<SyntheticMerchant>> map = new HashMap<>();
        for (UUID repId : repIds) map.put(repId, new ArrayList<>());
        for (SyntheticMerchant m : merchants) {
            UUID repId = repIds.get(
                    (m.syntheticId().hashCode() & Integer.MAX_VALUE) % repIds.size());
            map.get(repId).add(m);
        }
        return map;
    }

    /**
     * Randomly select {@code ceil(N_REPS × UNDERPERFORMANCE_RATE)} reps to exhibit
     * underperformance patterns. Minimum 1 if any reps exist.
     */
    private Set<UUID> selectUnderperformers(List<UUID> repIds, Random rng) {
        int count = Math.max(1, (int) Math.round(repIds.size() * UNDERPERFORMANCE_RATE));
        count = Math.min(count, repIds.size());
        List<UUID> shuffled = new ArrayList<>(repIds);
        Collections.shuffle(shuffled, rng);
        return new HashSet<>(shuffled.subList(0, count));
    }
}
