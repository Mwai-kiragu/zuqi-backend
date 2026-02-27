package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Generates a list of synthetic merchant profiles for a data generation run.
 *
 * <p>All randomness is driven by a single seeded {@link Random} instance constructed
 * from {@link SyntheticDataConfig#randomSeed()}, guaranteeing that the same config
 * produces an identical dataset every time.
 *
 * <p>Distribution parameters:
 * <ul>
 *   <li>Business categories: retail 60%, wholesale 25%, distributor 15%</li>
 *   <li>County distribution: weighted by merchant density via {@link KenyaGeography}</li>
 *   <li>Archetype distribution: per {@link SyntheticDataConfig#archetypeRatios()}</li>
 *   <li>Registration dates: uniform in the 6–24 months before generation date</li>
 *   <li>Initial credit limit: archetype mean order value × 4, rounded to nearest
 *       KES 1,000 (floor KES 5,000)</li>
 * </ul>
 *
 * <p>Business names are fetched in bulk (one batch per category) from the injected
 * {@link BusinessNameGenerator}. In production the Ollama-backed implementation is
 * used; in tests a mock or the fallback template generator is injected directly.
 */
@Component
@Slf4j
public class MerchantProfileGenerator {

    private static final String CAT_RETAIL      = "retail";
    private static final String CAT_WHOLESALE   = "wholesale";
    private static final String CAT_DISTRIBUTOR = "distributor";

    /** Category selection thresholds: retail [0, 0.60), wholesale [0.60, 0.85), distributor [0.85, 1.0). */
    private static final double RETAIL_THRESHOLD    = 0.60;
    private static final double WHOLESALE_THRESHOLD = 0.85;

    /** Minimum initial credit limit in KES. */
    private static final long MIN_CREDIT_LIMIT_KES = 5_000L;

    private final BusinessNameGenerator nameGenerator;

    public MerchantProfileGenerator(BusinessNameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generate {@link SyntheticDataConfig#merchantCount()} synthetic merchants.
     *
     * @param config generation parameters (seed, count, archetype ratios)
     * @return unmodifiable list of synthetic merchant DTOs
     */
    public List<SyntheticMerchant> generate(SyntheticDataConfig config) {
        Random rng   = new Random(config.randomSeed());
        int    total = config.merchantCount();

        log.info("Generating {} synthetic merchants (seed={})", total, config.randomSeed());

        // 1. Assign archetypes so distribution matches ratios exactly
        List<MerchantArchetype> archetypes = buildArchetypeList(config, total, rng);

        // 2. Prefetch business names per category in bulk (one LLM call per category)
        int batchSize = total + 20;  // slight buffer
        long seed     = config.randomSeed();
        Map<String, List<String>> namesByCategory = Map.of(
                CAT_RETAIL,      nameGenerator.generateBatch(CAT_RETAIL,      batchSize, seed),
                CAT_WHOLESALE,   nameGenerator.generateBatch(CAT_WHOLESALE,   batchSize, seed + 1),
                CAT_DISTRIBUTOR, nameGenerator.generateBatch(CAT_DISTRIBUTOR, batchSize, seed + 2)
        );

        // Track how many names have been consumed per category
        Map<String, Integer> nameIndex = new HashMap<>();
        nameIndex.put(CAT_RETAIL,      0);
        nameIndex.put(CAT_WHOLESALE,   0);
        nameIndex.put(CAT_DISTRIBUTOR, 0);

        LocalDate today    = LocalDate.now();
        List<SyntheticMerchant> merchants = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            MerchantArchetype archetype = archetypes.get(i);
            String            category  = sampleCategory(rng);

            // Name
            List<String> names = namesByCategory.get(category);
            int idx = nameIndex.get(category);
            String name = names.get(idx % names.size());
            nameIndex.put(category, idx + 1);

            // Location
            KenyaGeography.County    county = KenyaGeography.sampleCounty(rng);
            KenyaGeography.SubCounty subCty = KenyaGeography.sampleSubCounty(county, rng);
            double lat = KenyaGeography.sampleLat(subCty, rng);
            double lng = KenyaGeography.sampleLng(subCty, rng);

            // Registration date
            LocalDate regDate = sampleRegistrationDate(today, rng);

            // Credit limit
            BigDecimal creditLimit = deriveInitialCreditLimit(archetype);

            merchants.add(new SyntheticMerchant(
                    new UUID(rng.nextLong(), rng.nextLong()),
                    name,
                    category,
                    county.name(),
                    subCty.name(),
                    lat,
                    lng,
                    regDate,
                    creditLimit,
                    archetype
            ));
        }

        log.info("Generated {} synthetic merchants across {} counties",
                merchants.size(),
                merchants.stream().map(SyntheticMerchant::county).distinct().count());

        return Collections.unmodifiableList(merchants);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Build a shuffled list of archetypes matching the configured population ratios.
     *
     * <p>Integer rounding is handled via the largest-remainder method to guarantee
     * the list length equals {@code total} exactly.
     */
    private List<MerchantArchetype> buildArchetypeList(SyntheticDataConfig config,
                                                       int total,
                                                       Random rng) {
        Map<MerchantArchetype, Double> ratios = config.archetypeRatios();
        List<MerchantArchetype> list = new ArrayList<>(total);
        Map<MerchantArchetype, Double> remainders = new EnumMap<>(MerchantArchetype.class);

        int assigned = 0;
        for (MerchantArchetype a : MerchantArchetype.values()) {
            double ratio = ratios.getOrDefault(a, 0.0);
            int    floor = (int) (ratio * total);
            remainders.put(a, ratio * total - floor);
            for (int j = 0; j < floor; j++) list.add(a);
            assigned += floor;
        }

        // Distribute remaining slots to archetypes with the largest remainders
        int deficit = total - assigned;
        remainders.entrySet().stream()
                .sorted(Map.Entry.<MerchantArchetype, Double>comparingByValue().reversed())
                .limit(deficit)
                .forEach(e -> list.add(e.getKey()));

        Collections.shuffle(list, rng);
        return list;
    }

    /** Assign a business category based on the fixed 60/25/15 weight distribution. */
    private String sampleCategory(Random rng) {
        double roll = rng.nextDouble();
        if (roll < RETAIL_THRESHOLD)    return CAT_RETAIL;
        if (roll < WHOLESALE_THRESHOLD) return CAT_WHOLESALE;
        return CAT_DISTRIBUTOR;
    }

    /**
     * Sample a registration date uniformly in the 6–24 months before {@code today}.
     * An additional 0–29 day jitter is applied within the selected month.
     */
    private LocalDate sampleRegistrationDate(LocalDate today, Random rng) {
        int monthsBack = 6 + rng.nextInt(19);   // [6, 24] inclusive
        int daysJitter = rng.nextInt(30);
        return today.minusMonths(monthsBack).minusDays(daysJitter);
    }

    /**
     * Derive initial credit limit: archetype mean order value × 4,
     * rounded to the nearest KES 1,000 (floor KES 5,000).
     */
    private BigDecimal deriveInitialCreditLimit(MerchantArchetype archetype) {
        double raw     = archetype.orderValueMeanKes * 4.0;
        long   rounded = Math.round(raw / 1_000.0) * 1_000L;
        return BigDecimal.valueOf(Math.max(MIN_CREDIT_LIMIT_KES, rounded));
    }
}
