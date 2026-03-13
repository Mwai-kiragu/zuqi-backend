package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import com.zuqi.ai.synthetic.dto.SyntheticRepActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SalesRepActivityGeneratorTest {

    @Mock
    private BusinessNameGenerator nameGenerator;

    private MerchantProfileGenerator  merchantGenerator;
    private SalesRepActivityGenerator activityGenerator;

    private static final long SEED = 42L;
    private static final SyntheticDataConfig CONFIG = new SyntheticDataConfig(
            null, 30, 12, SEED, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
    /** 100 merchants → ~10 per rep; visit decay is not masked by territory clamping. */
    private static final SyntheticDataConfig LARGE_CONFIG = new SyntheticDataConfig(
            null, 100, 12, SEED, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);

    @BeforeEach
    void setUp() {
        lenient().when(nameGenerator.generateBatch(anyString(), anyInt(), anyLong()))
                .thenAnswer(inv -> {
                    String cat   = inv.getArgument(0);
                    int    count = inv.getArgument(1);
                    List<String> names = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) names.add(cat + " Biz " + i);
                    return names;
                });
        merchantGenerator  = new MerchantProfileGenerator(nameGenerator);
        activityGenerator  = new SalesRepActivityGenerator();
    }

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    private List<SyntheticMerchant> merchants() {
        return merchantGenerator.generate(CONFIG);
    }

    // -------------------------------------------------------------------------
    // Basic correctness
    // -------------------------------------------------------------------------

    @Test
    void generate_shouldReturnNonEmptyList() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants(), CONFIG);
        assertThat(activities).isNotNull().isNotEmpty();
    }

    @Test
    void generate_resultShouldBeUnmodifiable() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants(), CONFIG);
        assertThatThrownBy(() -> activities.add(activities.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void generate_allActivitiesShouldHaveRequiredFields() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants(), CONFIG);
        for (SyntheticRepActivity a : activities) {
            assertThat(a.syntheticId()).isNotNull();
            assertThat(a.salesRepId()).isNotNull();
            assertThat(a.merchantRef()).isNotNull();
            assertThat(a.visitDate()).isNotNull();
            assertThat(a.orderValue()).isNotNull();
            assertThat(a.visitDurationMinutes()).isGreaterThan(0);
        }
    }

    // -------------------------------------------------------------------------
    // Date constraints
    // -------------------------------------------------------------------------

    @Test
    void generate_visitDatesShouldBeWithinHistoryWindow() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants(), CONFIG);
        LocalDate historyStart = LocalDate.now().minusMonths(CONFIG.historyMonths());
        LocalDate historyEnd   = LocalDate.now();
        assertThat(activities)
                .extracting(SyntheticRepActivity::visitDate)
                .allMatch(d -> !d.isBefore(historyStart) && !d.isAfter(historyEnd),
                        "all visit dates should fall within the history window");
    }

    @Test
    void generate_noActivitiesOnSunday() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants(), CONFIG);
        assertThat(activities)
                .extracting(a -> a.visitDate().getDayOfWeek())
                .doesNotContain(DayOfWeek.SUNDAY);
    }

    // -------------------------------------------------------------------------
    // Order value constraints
    // -------------------------------------------------------------------------

    @Test
    void generate_orderValueShouldBeZeroWhenNoOrderPlaced() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants(), CONFIG);
        assertThat(activities.stream()
                .filter(a -> !a.orderPlaced())
                .allMatch(a -> a.orderValue().compareTo(BigDecimal.ZERO) == 0))
                .isTrue();
    }

    @Test
    void generate_orderValueShouldBePositiveWhenOrderPlaced() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants(), CONFIG);
        assertThat(activities.stream()
                .filter(SyntheticRepActivity::orderPlaced)
                .allMatch(a -> a.orderValue().compareTo(BigDecimal.ZERO) > 0))
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // Relational integrity
    // -------------------------------------------------------------------------

    @Test
    void generate_allMerchantRefsShouldLinkToKnownMerchants() {
        List<SyntheticMerchant> merchants = merchants();
        Set<UUID> merchantIds = merchants.stream()
                .map(SyntheticMerchant::syntheticId)
                .collect(Collectors.toSet());
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants, CONFIG);
        assertThat(activities)
                .extracting(SyntheticRepActivity::merchantRef)
                .allMatch(merchantIds::contains, "every merchantRef should point to a known merchant");
    }

    @Test
    void generate_eachMerchantShouldBeVisitedMultipleTimes() {
        List<SyntheticMerchant> merchants = merchants();
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants, CONFIG);
        Map<UUID, Long> visitsPerMerchant = activities.stream()
                .collect(Collectors.groupingBy(SyntheticRepActivity::merchantRef,
                        Collectors.counting()));
        // With 12 months Mon-Sat, each merchant should get many visits
        assertThat(visitsPerMerchant.values())
                .allMatch(count -> count >= 5,
                        "each merchant should be visited at least 5 times over 12 months");
    }

    // -------------------------------------------------------------------------
    // Underperformance signals
    // -------------------------------------------------------------------------

    @Test
    void generate_underperformingRepsShouldExist() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants(), CONFIG);
        long underperformingCount = activities.stream()
                .filter(SyntheticRepActivity::isUnderperforming)
                .count();
        assertThat(underperformingCount)
                .as("at least some activities should be flagged as underperforming")
                .isGreaterThan(0);
    }

    @Test
    void generate_normalRepsShouldHaveHigherConversionThanUnderperforming() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants(), CONFIG);

        // Focus on the second half of the history (underperformance is pronounced there)
        LocalDate midpoint = LocalDate.now().minusMonths(CONFIG.historyMonths() / 2);

        double normalConversion = activities.stream()
                .filter(a -> !a.isUnderperforming() && a.visitDate().isAfter(midpoint))
                .mapToInt(a -> a.orderPlaced() ? 1 : 0)
                .average()
                .orElseThrow(() -> new AssertionError("No normal rep activities in second half"));

        double underConversion = activities.stream()
                .filter(a -> a.isUnderperforming() && a.visitDate().isAfter(midpoint))
                .mapToInt(a -> a.orderPlaced() ? 1 : 0)
                .average()
                .orElseThrow(() -> new AssertionError("No underperforming activities in second half"));

        assertThat(normalConversion)
                .as("normal reps (%.2f) should convert at higher rate than underperforming (%.2f)",
                        normalConversion, underConversion)
                .isGreaterThan(underConversion);
    }

    @Test
    void generate_underperformingRepsShouldHaveShorterVisitDurations() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(merchants(), CONFIG);

        // Compare late history (months 7-12) where the underperformance signal is strongest
        LocalDate lateStart = LocalDate.now().minusMonths(5);

        double normalAvgDuration = activities.stream()
                .filter(a -> !a.isUnderperforming() && a.visitDate().isAfter(lateStart))
                .mapToInt(SyntheticRepActivity::visitDurationMinutes)
                .average()
                .orElseThrow(() -> new AssertionError("No normal activities in late history"));

        double underAvgDuration = activities.stream()
                .filter(a -> a.isUnderperforming() && a.visitDate().isAfter(lateStart))
                .mapToInt(SyntheticRepActivity::visitDurationMinutes)
                .average()
                .orElseThrow(() -> new AssertionError("No underperforming activities in late history"));

        assertThat(normalAvgDuration)
                .as("normal reps avg duration (%.1f min) should exceed underperforming (%.1f min)",
                        normalAvgDuration, underAvgDuration)
                .isGreaterThan(underAvgDuration);
    }

    @Test
    void generate_underperformingRepsShouldHaveFewerVisitsLateInHistory() {
        // Use 100 merchants (~10/rep) so visits per day are NOT clamped to territory size
        // and the decay multiplier is visible in the output counts.
        List<SyntheticMerchant> bigMerchants = merchantGenerator.generate(LARGE_CONFIG);
        List<SyntheticRepActivity> activities = activityGenerator.generate(bigMerchants, LARGE_CONFIG);

        // months 0-2 of history (before activation at month 3): full visit counts
        LocalDate historyStart = LocalDate.now().minusMonths(LARGE_CONFIG.historyMonths());
        LocalDate earlyEnd   = historyStart.plusMonths(2);
        // months 9-12 of history (active ~6-9 months): heavily decayed
        LocalDate lateStart  = LocalDate.now().minusMonths(3);

        Map<UUID, Long> earlyVisits = activities.stream()
                .filter(a -> a.isUnderperforming()
                        && !a.visitDate().isBefore(historyStart)
                        && a.visitDate().isBefore(earlyEnd))
                .collect(Collectors.groupingBy(SyntheticRepActivity::salesRepId, Collectors.counting()));

        Map<UUID, Long> lateVisits = activities.stream()
                .filter(a -> a.isUnderperforming() && a.visitDate().isAfter(lateStart))
                .collect(Collectors.groupingBy(SyntheticRepActivity::salesRepId, Collectors.counting()));

        assertThat(earlyVisits).isNotEmpty();
        assertThat(lateVisits).isNotEmpty();

        // early window ~2 months, late window ~3 months; normalise per month
        double earlyPerMonth = earlyVisits.values().stream().mapToLong(Long::longValue).sum() / 2.0;
        double latePerMonth  = lateVisits.values().stream().mapToLong(Long::longValue).sum() / 3.0;

        assertThat(earlyPerMonth)
                .as("early monthly visits (%.1f) should exceed late (%.1f) for underperforming reps",
                        earlyPerMonth, latePerMonth)
                .isGreaterThan(latePerMonth);
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void generate_worksWithEmptyMerchantList() {
        List<SyntheticRepActivity> activities = activityGenerator.generate(List.of(), CONFIG);
        assertThat(activities).isNotNull().isEmpty();
    }

    @Test
    void generate_worksWithSingleMerchant() {
        List<SyntheticMerchant> single = merchants().subList(0, 1);
        List<SyntheticRepActivity> activities = activityGenerator.generate(single, CONFIG);
        assertThat(activities).isNotEmpty();
        // The single merchant should be visited
        assertThat(activities)
                .extracting(SyntheticRepActivity::merchantRef)
                .containsOnly(single.get(0).syntheticId());
    }

    // -------------------------------------------------------------------------
    // Reproducibility
    // -------------------------------------------------------------------------

    @Test
    void generate_isDeterministic() {
        List<SyntheticMerchant> merchants = merchants();
        List<SyntheticRepActivity> first  = activityGenerator.generate(merchants, CONFIG);
        List<SyntheticRepActivity> second = activityGenerator.generate(merchants, CONFIG);
        assertThat(first).hasSameSizeAs(second);
    }
}
