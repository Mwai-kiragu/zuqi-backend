package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticMerchant;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantProfileGeneratorTest {

    @Mock
    private BusinessNameGenerator nameGenerator;

    private MerchantProfileGenerator generator;

    @BeforeEach
    void setUp() {
        // Stub: return N distinct names for any category+count combination
        when(nameGenerator.generateBatch(anyString(), anyInt(), anyLong()))
                .thenAnswer(inv -> {
                    String category = inv.getArgument(0);
                    int    count    = inv.getArgument(1);
                    List<String> names = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        names.add(category + " Business " + i);
                    }
                    return names;
                });

        generator = new MerchantProfileGenerator(nameGenerator);
    }

    // -------------------------------------------------------------------------
    // Basic correctness
    // -------------------------------------------------------------------------

    @Test
    void generate_shouldProduceExactMerchantCount() {
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 42L);
        List<SyntheticMerchant> merchants = generator.generate(config);
        assertThat(merchants).hasSize(config.merchantCount());
    }

    @Test
    void generate_allFieldsShouldBeNonNull() {
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 7L);
        for (SyntheticMerchant m : generator.generate(config)) {
            assertThat(m.syntheticId()).isNotNull();
            assertThat(m.businessName()).isNotBlank();
            assertThat(m.businessCategory()).isNotBlank();
            assertThat(m.county()).isNotBlank();
            assertThat(m.subCounty()).isNotBlank();
            assertThat(m.registrationDate()).isNotNull();
            assertThat(m.initialCreditLimit()).isNotNull();
            assertThat(m.merchantArchetype()).isNotNull();
        }
    }

    @Test
    void generate_resultShouldBeUnmodifiable() {
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 1L);
        List<SyntheticMerchant> merchants = generator.generate(config);
        assertThat(merchants).isNotEmpty();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> merchants.add(merchants.get(0))
        );
    }

    // -------------------------------------------------------------------------
    // Archetype distribution
    // -------------------------------------------------------------------------

    @Test
    void generate_archetypeDistribution_steadyGrowerShouldBe35Percent() {
        // Use 1000 merchants for statistical stability
        SyntheticDataConfig config = new SyntheticDataConfig(
                UUID.randomUUID(), 1000, 12, 42L, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
        List<SyntheticMerchant> merchants = generator.generate(config);

        long steadyGrowers = merchants.stream()
                .filter(m -> m.merchantArchetype() == MerchantArchetype.STEADY_GROWER)
                .count();
        // Expected: 350 (±50 to account for largest-remainder rounding, not sampling noise)
        assertThat(steadyGrowers).isBetween(330L, 370L);
    }

    @Test
    void generate_archetypeDistribution_defaulterShouldBe3Percent() {
        SyntheticDataConfig config = new SyntheticDataConfig(
                UUID.randomUUID(), 1000, 12, 42L, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
        List<SyntheticMerchant> merchants = generator.generate(config);

        long defaulters = merchants.stream()
                .filter(m -> m.merchantArchetype() == MerchantArchetype.DEFAULTER)
                .count();
        // Expected: 30 (±15)
        assertThat(defaulters).isBetween(15L, 45L);
    }

    @Test
    void generate_archetypeDistribution_allArchetypesPresent_forSufficientCount() {
        SyntheticDataConfig config = new SyntheticDataConfig(
                UUID.randomUUID(), 500, 12, 99L, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
        List<SyntheticMerchant> merchants = generator.generate(config);

        for (MerchantArchetype archetype : MerchantArchetype.values()) {
            long count = merchants.stream()
                    .filter(m -> m.merchantArchetype() == archetype)
                    .count();
            assertThat(count)
                    .as("Archetype %s should be present", archetype)
                    .isGreaterThan(0);
        }
    }

    @Test
    void generate_archetypeList_sumShouldEqualTotal() {
        SyntheticDataConfig config = new SyntheticDataConfig(
                UUID.randomUUID(), 100, 12, 1L, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
        List<SyntheticMerchant> merchants = generator.generate(config);

        long total = merchants.stream()
                .collect(Collectors.groupingBy(SyntheticMerchant::merchantArchetype, Collectors.counting()))
                .values().stream().mapToLong(Long::longValue).sum();
        assertThat(total).isEqualTo(100L);
    }

    // -------------------------------------------------------------------------
    // Business categories
    // -------------------------------------------------------------------------

    @Test
    void generate_businessCategories_shouldOnlyContainExpectedValues() {
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 5L);
        generator.generate(config).forEach(m ->
                assertThat(m.businessCategory()).isIn("retail", "wholesale", "distributor")
        );
    }

    @Test
    void generate_businessCategories_retailShouldBeApprox60Percent() {
        SyntheticDataConfig config = new SyntheticDataConfig(
                UUID.randomUUID(), 1000, 12, 42L, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
        List<SyntheticMerchant> merchants = generator.generate(config);

        Map<String, Long> byCat = merchants.stream()
                .collect(Collectors.groupingBy(SyntheticMerchant::businessCategory, Collectors.counting()));

        assertThat(byCat.get("retail"))
                .as("retail should be around 60%")
                .isBetween(500L, 700L);
        assertThat(byCat.get("wholesale"))
                .as("wholesale should be around 25%")
                .isBetween(180L, 320L);
        assertThat(byCat.get("distributor"))
                .as("distributor should be around 15%")
                .isBetween(80L, 220L);
    }

    // -------------------------------------------------------------------------
    // County distribution
    // -------------------------------------------------------------------------

    @Test
    void generate_countyDistribution_nairobiShouldBeCommonest() {
        SyntheticDataConfig config = new SyntheticDataConfig(
                UUID.randomUUID(), 500, 12, 42L, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
        List<SyntheticMerchant> merchants = generator.generate(config);

        Map<String, Long> byCounty = merchants.stream()
                .collect(Collectors.groupingBy(SyntheticMerchant::county, Collectors.counting()));

        long nairobi = byCounty.getOrDefault("Nairobi", 0L);
        // Nairobi has weight 0.30 — should be highest
        byCounty.forEach((county, count) ->
                assertThat(nairobi)
                        .as("Nairobi (%d) should have at least as many merchants as %s (%d)", nairobi, county, count)
                        .isGreaterThanOrEqualTo(count)
        );
    }

    @Test
    void generate_countyDistribution_multiplCountiesRepresented() {
        SyntheticDataConfig config = new SyntheticDataConfig(
                UUID.randomUUID(), 500, 12, 42L, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
        List<SyntheticMerchant> merchants = generator.generate(config);

        long distinctCounties = merchants.stream().map(SyntheticMerchant::county).distinct().count();
        // With 500 merchants and 12 counties, all should appear
        assertThat(distinctCounties).isGreaterThanOrEqualTo(10);
    }

    // -------------------------------------------------------------------------
    // GPS coordinates
    // -------------------------------------------------------------------------

    @Test
    void generate_gpsCoordinates_latitudeShouldBeWithinKenyaBounds() {
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 13L);
        generator.generate(config).forEach(m ->
                assertThat(m.gpsLat())
                        .as("Latitude for %s in %s must be within Kenya bounds", m.businessName(), m.county())
                        .isBetween(-4.7, 4.6)
        );
    }

    @Test
    void generate_gpsCoordinates_longitudeShouldBeWithinKenyaBounds() {
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 13L);
        generator.generate(config).forEach(m ->
                assertThat(m.gpsLng())
                        .as("Longitude for %s in %s must be within Kenya bounds", m.businessName(), m.county())
                        .isBetween(33.9, 41.9)
        );
    }

    // -------------------------------------------------------------------------
    // Registration dates
    // -------------------------------------------------------------------------

    @Test
    void generate_registrationDates_shouldBeBetween6And24MonthsAgo() {
        LocalDate today = LocalDate.now();
        LocalDate earliest = today.minusMonths(25); // allow slight buffer for day jitter
        LocalDate latest   = today.minusMonths(5);  // allow slight buffer for day jitter

        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 21L);
        generator.generate(config).forEach(m ->
                assertThat(m.registrationDate())
                        .as("Registration date for %s", m.businessName())
                        .isBetween(earliest, latest)
        );
    }

    @Test
    void generate_registrationDates_shouldBeInThePast() {
        LocalDate today = LocalDate.now();
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 33L);
        generator.generate(config).forEach(m ->
                assertThat(m.registrationDate()).isBefore(today)
        );
    }

    // -------------------------------------------------------------------------
    // Credit limits
    // -------------------------------------------------------------------------

    @Test
    void generate_creditLimits_steadyGrowerShouldBe100000() {
        // STEADY_GROWER: orderValueMeanKes=25000, × 4 = 100000, rounded to 1000
        SyntheticDataConfig config = new SyntheticDataConfig(
                UUID.randomUUID(), 200, 12, 42L,
                Map.of(MerchantArchetype.STEADY_GROWER, 1.0));
        List<SyntheticMerchant> merchants = generator.generate(config);

        merchants.forEach(m ->
                assertThat(m.initialCreditLimit().longValue())
                        .as("STEADY_GROWER credit limit should be 100000")
                        .isEqualTo(100_000L)
        );
    }

    @Test
    void generate_creditLimits_shouldNeverBelowMinimum() {
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 77L);
        generator.generate(config).forEach(m ->
                assertThat(m.initialCreditLimit().longValue())
                        .as("Credit limit should never be below KES 5000")
                        .isGreaterThanOrEqualTo(5_000L)
        );
    }

    @Test
    void generate_creditLimits_shouldBeMultipleOf1000() {
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 55L);
        generator.generate(config).forEach(m ->
                assertThat(m.initialCreditLimit().longValue() % 1_000L)
                        .as("Credit limit for %s should be a multiple of 1000", m.businessName())
                        .isZero()
        );
    }

    // -------------------------------------------------------------------------
    // Determinism
    // -------------------------------------------------------------------------

    @Test
    void generate_sameSeedShouldProduceSameArchetypeSequence() {
        SyntheticDataConfig cfg1 = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 99L);
        SyntheticDataConfig cfg2 = SyntheticDataConfig.defaultConfig(UUID.randomUUID(), 99L);

        List<SyntheticMerchant> run1 = generator.generate(cfg1);
        List<SyntheticMerchant> run2 = generator.generate(cfg2);

        // Same seed → same archetype sequence (distributorId irrelevant to generation)
        for (int i = 0; i < run1.size(); i++) {
            assertThat(run1.get(i).merchantArchetype())
                    .isEqualTo(run2.get(i).merchantArchetype());
            assertThat(run1.get(i).county())
                    .isEqualTo(run2.get(i).county());
            assertThat(run1.get(i).businessCategory())
                    .isEqualTo(run2.get(i).businessCategory());
        }
    }
}
