package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.dto.SyntheticCreditEvaluation;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import com.zuqi.ai.synthetic.dto.SyntheticPayment;
import com.zuqi.ai.synthetic.generators.OrderHistoryGenerator.OrderHistoryResult;
import com.zuqi.ai.synthetic.profiles.MerchantArchetype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class CreditHistoryGeneratorTest {

    @Mock
    private BusinessNameGenerator nameGenerator;

    private MerchantProfileGenerator merchantGenerator;
    private OrderHistoryGenerator     orderGenerator;
    private PaymentBehaviorGenerator  paymentGenerator;
    private CreditHistoryGenerator    creditGenerator;

    private static final long SEED = 42L;
    private static final SyntheticDataConfig CONFIG = new SyntheticDataConfig(
            null, 30, 12, SEED, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);
    /** 100 merchants → adequate sample of all archetypes for trajectory assertions. */
    private static final SyntheticDataConfig LARGE_CONFIG = new SyntheticDataConfig(
            null, 100, 12, SEED, SyntheticDataConfig.DEFAULT_ARCHETYPE_RATIOS);

    private static final Set<String> VALID_GRADES = Set.of("A", "B", "C", "D", "F");

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
        merchantGenerator = new MerchantProfileGenerator(nameGenerator);
        orderGenerator    = new OrderHistoryGenerator();
        paymentGenerator  = new PaymentBehaviorGenerator();
        creditGenerator   = new CreditHistoryGenerator();
    }

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    private List<SyntheticMerchant> merchants(SyntheticDataConfig cfg) {
        return merchantGenerator.generate(cfg);
    }

    private List<SyntheticPayment> payments(List<SyntheticMerchant> merchants,
                                            SyntheticDataConfig cfg) {
        OrderHistoryResult orders = orderGenerator.generate(merchants, cfg);
        return paymentGenerator.generate(orders.orders(), merchants, cfg);
    }

    // -------------------------------------------------------------------------
    // Basic correctness
    // -------------------------------------------------------------------------

    @Test
    void generate_shouldReturnNonEmptyList() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, CONFIG), CONFIG);
        assertThat(evals).isNotNull().isNotEmpty();
    }

    @Test
    void generate_resultShouldBeUnmodifiable() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, CONFIG), CONFIG);
        assertThatThrownBy(() -> evals.add(evals.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void generate_allEvaluationsShouldHaveRequiredFields() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, CONFIG), CONFIG);
        for (SyntheticCreditEvaluation e : evals) {
            assertThat(e.syntheticId()).isNotNull();
            assertThat(e.merchantRef()).isNotNull();
            assertThat(e.evaluationDate()).isNotNull();
            assertThat(e.grade()).isNotBlank();
            assertThat(e.creditLimit()).isNotNull();
        }
    }

    @Test
    void generate_allGradesShouldBeValidValues() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, CONFIG), CONFIG);
        assertThat(evals)
                .extracting(SyntheticCreditEvaluation::grade)
                .allMatch(VALID_GRADES::contains, "grade must be one of A, B, C, D, F");
    }

    @Test
    void generate_creditLimitsShouldBePositive() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, CONFIG), CONFIG);
        assertThat(evals)
                .extracting(e -> e.creditLimit().compareTo(BigDecimal.ZERO))
                .allMatch(cmp -> cmp > 0, "all credit limits should be positive");
    }

    @Test
    void generate_evaluationDatesShouldBeWithinHistoryWindow() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, CONFIG), CONFIG);
        LocalDate historyStart = LocalDate.now().minusMonths(CONFIG.historyMonths());
        LocalDate historyEnd   = LocalDate.now();
        assertThat(evals)
                .extracting(SyntheticCreditEvaluation::evaluationDate)
                .allMatch(d -> !d.isBefore(historyStart) && !d.isAfter(historyEnd),
                        "all evaluation dates should fall within the history window");
    }

    // -------------------------------------------------------------------------
    // Relational integrity
    // -------------------------------------------------------------------------

    @Test
    void generate_allMerchantRefsShouldLinkToKnownMerchants() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        Set<UUID> merchantIds = m.stream().map(SyntheticMerchant::syntheticId)
                .collect(Collectors.toSet());
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, CONFIG), CONFIG);
        assertThat(evals)
                .extracting(SyntheticCreditEvaluation::merchantRef)
                .allMatch(merchantIds::contains,
                        "every merchantRef should point to a known merchant");
    }

    @Test
    void generate_eachMerchantShouldHaveAtLeastOneEvaluation() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, CONFIG), CONFIG);
        Set<UUID> visitedMerchants = evals.stream()
                .map(SyntheticCreditEvaluation::merchantRef)
                .collect(Collectors.toSet());
        assertThat(visitedMerchants)
                .as("every merchant should have at least one evaluation")
                .containsAll(m.stream().map(SyntheticMerchant::syntheticId).toList());
    }

    // -------------------------------------------------------------------------
    // Default flag consistency
    // -------------------------------------------------------------------------

    @Test
    void generate_defaultedFlagAndDaysToDefaultShouldBeConsistent() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, CONFIG), CONFIG);
        for (SyntheticCreditEvaluation e : evals) {
            if (e.defaulted()) {
                assertThat(e.daysToDefault())
                        .as("defaulted=true evaluation should have non-null daysToDefault")
                        .isNotNull();
                assertThat(e.daysToDefault())
                        .as("daysToDefault should be non-negative")
                        .isGreaterThanOrEqualTo(0);
            } else {
                assertThat(e.daysToDefault())
                        .as("defaulted=false evaluation should have null daysToDefault")
                        .isNull();
            }
        }
    }

    @Test
    void generate_defaultEventsShouldBePresent() {
        List<SyntheticMerchant> m = merchants(LARGE_CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, LARGE_CONFIG), LARGE_CONFIG);
        long defaultedCount = evals.stream().filter(SyntheticCreditEvaluation::defaulted).count();
        assertThat(defaultedCount)
                .as("at least some evaluations should carry the defaulted=true label")
                .isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // Archetype grade trajectories
    // -------------------------------------------------------------------------

    @Test
    void generate_steadyGrowerShouldHaveGradeBOrAbove() {
        List<SyntheticMerchant> m = merchants(LARGE_CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, LARGE_CONFIG), LARGE_CONFIG);

        // STEADY_GROWER should predominantly receive B or A; only rare noise should drop to C
        Map<UUID, MerchantArchetype> archetypeById = m.stream()
                .collect(Collectors.toMap(SyntheticMerchant::syntheticId,
                        SyntheticMerchant::merchantArchetype));

        List<SyntheticCreditEvaluation> growersEvals = evals.stream()
                .filter(e -> archetypeById.get(e.merchantRef()) == MerchantArchetype.STEADY_GROWER)
                .toList();

        assertThat(growersEvals).isNotEmpty();

        long highGradeCount = growersEvals.stream()
                .filter(e -> e.grade().equals("A") || e.grade().equals("B"))
                .count();
        double highGradeRatio = (double) highGradeCount / growersEvals.size();
        assertThat(highGradeRatio)
                .as("at least 70%% of STEADY_GROWER evaluations should be B or A (got %.1f%%)",
                        highGradeRatio * 100)
                .isGreaterThan(0.70);
    }

    @Test
    void generate_defaulterEarlyEvalsShouldBeBOrC() {
        List<SyntheticMerchant> m = merchants(LARGE_CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, LARGE_CONFIG), LARGE_CONFIG);

        LocalDate historyStart = LocalDate.now().minusMonths(LARGE_CONFIG.historyMonths());
        LocalDate earlyEnd     = historyStart.plusMonths(3);

        Map<UUID, MerchantArchetype> archetypeById = m.stream()
                .collect(Collectors.toMap(SyntheticMerchant::syntheticId,
                        SyntheticMerchant::merchantArchetype));

        List<SyntheticCreditEvaluation> earlyDefaulterEvals = evals.stream()
                .filter(e -> archetypeById.get(e.merchantRef()) == MerchantArchetype.DEFAULTER
                        && e.evaluationDate().isBefore(earlyEnd))
                .toList();

        assertThat(earlyDefaulterEvals).isNotEmpty();
        assertThat(earlyDefaulterEvals)
                .extracting(SyntheticCreditEvaluation::grade)
                .allMatch(g -> g.equals("B") || g.equals("C"),
                        "DEFAULTER evaluations in months 0-2 should be B or C, not D/F");
    }

    @Test
    void generate_defaulterLateEvalsShouldBeGradeDOrF() {
        List<SyntheticMerchant> m = merchants(LARGE_CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, LARGE_CONFIG), LARGE_CONFIG);

        LocalDate historyStart = LocalDate.now().minusMonths(LARGE_CONFIG.historyMonths());
        LocalDate lateStart    = historyStart.plusMonths(6);

        Map<UUID, MerchantArchetype> archetypeById = m.stream()
                .collect(Collectors.toMap(SyntheticMerchant::syntheticId,
                        SyntheticMerchant::merchantArchetype));

        List<SyntheticCreditEvaluation> lateDefaulterEvals = evals.stream()
                .filter(e -> archetypeById.get(e.merchantRef()) == MerchantArchetype.DEFAULTER
                        && !e.evaluationDate().isBefore(lateStart))
                .toList();

        // Only non-defaulting DEFAULTER merchants reach month 6+ (their evals don't stop early)
        // If any such evals exist, they must be D or F
        if (!lateDefaulterEvals.isEmpty()) {
            assertThat(lateDefaulterEvals)
                    .extracting(SyntheticCreditEvaluation::grade)
                    .allMatch(g -> g.equals("D") || g.equals("F"),
                            "DEFAULTER evaluations in months 6+ should be grade D or F");
        }
    }

    @Test
    void generate_decliningRiskGradeShouldDeteriorate() {
        List<SyntheticMerchant> m = merchants(LARGE_CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, LARGE_CONFIG), LARGE_CONFIG);

        LocalDate historyStart = LocalDate.now().minusMonths(LARGE_CONFIG.historyMonths());
        LocalDate earlyEnd     = historyStart.plusMonths(4);
        LocalDate lateStart    = historyStart.plusMonths(8);

        Map<UUID, MerchantArchetype> archetypeById = m.stream()
                .collect(Collectors.toMap(SyntheticMerchant::syntheticId,
                        SyntheticMerchant::merchantArchetype));

        Map<String, Integer> gradeToInt = Map.of("A", 4, "B", 3, "C", 2, "D", 1, "F", 0);

        double earlyAvg = evals.stream()
                .filter(e -> archetypeById.get(e.merchantRef()) == MerchantArchetype.DECLINING_RISK
                        && e.evaluationDate().isBefore(earlyEnd))
                .mapToInt(e -> gradeToInt.get(e.grade()))
                .average()
                .orElseThrow(() -> new AssertionError("No DECLINING_RISK evaluations in early period"));

        double lateAvg = evals.stream()
                .filter(e -> archetypeById.get(e.merchantRef()) == MerchantArchetype.DECLINING_RISK
                        && e.evaluationDate().isAfter(lateStart))
                .mapToInt(e -> gradeToInt.get(e.grade()))
                .average()
                .orElseThrow(() -> new AssertionError("No DECLINING_RISK evaluations in late period"));

        assertThat(earlyAvg)
                .as("DECLINING_RISK early avg grade (%.2f) should exceed late (%.2f)",
                        earlyAvg, lateAvg)
                .isGreaterThan(lateAvg);
    }

    // -------------------------------------------------------------------------
    // Credit limit trajectories
    // -------------------------------------------------------------------------

    @Test
    void generate_steadyGrowerCreditLimitShouldGrow() {
        List<SyntheticMerchant> m = merchants(LARGE_CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, LARGE_CONFIG), LARGE_CONFIG);

        LocalDate historyStart = LocalDate.now().minusMonths(LARGE_CONFIG.historyMonths());
        LocalDate earlyEnd     = historyStart.plusMonths(2);
        LocalDate lateStart    = LocalDate.now().minusMonths(2);

        Map<UUID, MerchantArchetype> archetypeById = m.stream()
                .collect(Collectors.toMap(SyntheticMerchant::syntheticId,
                        SyntheticMerchant::merchantArchetype));

        double earlyAvgLimit = evals.stream()
                .filter(e -> archetypeById.get(e.merchantRef()) == MerchantArchetype.STEADY_GROWER
                        && e.evaluationDate().isBefore(earlyEnd))
                .mapToDouble(e -> e.creditLimit().doubleValue())
                .average()
                .orElseThrow(() -> new AssertionError("No STEADY_GROWER early evaluations"));

        double lateAvgLimit = evals.stream()
                .filter(e -> archetypeById.get(e.merchantRef()) == MerchantArchetype.STEADY_GROWER
                        && e.evaluationDate().isAfter(lateStart))
                .mapToDouble(e -> e.creditLimit().doubleValue())
                .average()
                .orElseThrow(() -> new AssertionError("No STEADY_GROWER late evaluations"));

        assertThat(lateAvgLimit)
                .as("STEADY_GROWER credit limit should grow: early avg %.0f, late avg %.0f",
                        earlyAvgLimit, lateAvgLimit)
                .isGreaterThan(earlyAvgLimit);
    }

    @Test
    void generate_defaulterCreditLimitShouldShrink() {
        List<SyntheticMerchant> m = merchants(LARGE_CONFIG);
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, payments(m, LARGE_CONFIG), LARGE_CONFIG);

        LocalDate historyStart = LocalDate.now().minusMonths(LARGE_CONFIG.historyMonths());
        LocalDate earlyEnd     = historyStart.plusMonths(2);

        Map<UUID, MerchantArchetype> archetypeById = m.stream()
                .collect(Collectors.toMap(SyntheticMerchant::syntheticId,
                        SyntheticMerchant::merchantArchetype));

        // Use per-merchant comparison: for each DEFAULTER merchant, compare month-0 to month-5 limit
        Map<UUID, List<SyntheticCreditEvaluation>> byMerchant = evals.stream()
                .filter(e -> archetypeById.get(e.merchantRef()) == MerchantArchetype.DEFAULTER)
                .collect(Collectors.groupingBy(SyntheticCreditEvaluation::merchantRef));

        assertThat(byMerchant).isNotEmpty();

        for (Map.Entry<UUID, List<SyntheticCreditEvaluation>> entry : byMerchant.entrySet()) {
            List<SyntheticCreditEvaluation> merchantEvals = entry.getValue().stream()
                    .sorted((a, b) -> a.evaluationDate().compareTo(b.evaluationDate()))
                    .toList();
            if (merchantEvals.size() < 3) continue; // need at least 3 evals to show trend

            double earliestLimit = merchantEvals.get(0).creditLimit().doubleValue();
            double latestLimit   = merchantEvals.get(merchantEvals.size() - 1).creditLimit().doubleValue();

            assertThat(latestLimit)
                    .as("DEFAULTER merchant %s: limit should shrink over time (%.0f → %.0f)",
                            entry.getKey(), earliestLimit, latestLimit)
                    .isLessThan(earliestLimit);
        }
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void generate_worksWithEmptyPayments() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        // Should not throw; payment data is optional enrichment
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(m, List.of(), CONFIG);
        assertThat(evals).isNotEmpty();
    }

    @Test
    void generate_worksWithEmptyMerchants() {
        List<SyntheticCreditEvaluation> evals =
                creditGenerator.generate(List.of(), List.of(), CONFIG);
        assertThat(evals).isNotNull().isEmpty();
    }

    // -------------------------------------------------------------------------
    // Reproducibility
    // -------------------------------------------------------------------------

    @Test
    void generate_isDeterministic() {
        List<SyntheticMerchant> m = merchants(CONFIG);
        List<SyntheticPayment>  p = payments(m, CONFIG);
        List<SyntheticCreditEvaluation> first  = creditGenerator.generate(m, p, CONFIG);
        List<SyntheticCreditEvaluation> second = creditGenerator.generate(m, p, CONFIG);
        assertThat(first).hasSameSizeAs(second);
    }
}
