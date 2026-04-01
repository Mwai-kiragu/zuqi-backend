package com.zuqi.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.domain.ai.Recommendation;
import com.zuqi.domain.ai.RecommendationPriority;
import com.zuqi.domain.ai.RecommendationType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.RecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecommendationService} — LLM recommendation workflow.
 *
 * Uses a real {@link ObjectMapper} so JSON parsing behaviour is exercised
 * without mocking internals. The service is constructed manually in
 * {@code setUp()} to allow injection of the real ObjectMapper.
 *
 * Blueprint reference: implementation_plan.md Phase 6, Task 6.2
 */
@SuppressWarnings("DataFlowIssue")
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private RecommendationAgent recommendationAgent;

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private DistributorRepository distributorRepository;

    private RecommendationService service;

    private static final UUID DISTRIBUTOR_ID = UUID.randomUUID();

    /** Minimal valid single-recommendation JSON that the LLM might return. */
    private static final String VALID_JSON_ARRAY = """
            [
              {
                "recommendationType": "SALES_TREND",
                "observation": "Sales dipping 15% in Westlands region.",
                "evidence": {"weeklyDropPct": 15},
                "recommendation": "Increase rep visit frequency for Westlands merchants.",
                "expectedImpact": "Recover 10% of lost revenue within 30 days.",
                "priority": "HIGH"
              }
            ]
            """;

    @BeforeEach
    void setUp() {
        // Construct manually so we can inject a real ObjectMapper
        service = new RecommendationService(
                recommendationAgent,
                recommendationRepository,
                distributorRepository,
                new ObjectMapper()
        );

        // Default stub: saveAll returns an empty list (lenient — not every test calls it)
        lenient().when(recommendationRepository.saveAll(anyList()))
                .thenReturn(List.of());
    }

    // ── Test 1: Unknown distributor ──────────────────────────────────────────

    @Test
    void generateAndSave_unknownDistributor_throwsIllegalArgument() {
        when(distributorRepository.findById(DISTRIBUTOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateAndSave(DISTRIBUTOR_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(DISTRIBUTOR_ID.toString());
    }

    // ── Test 2: Valid JSON → saveAll called ──────────────────────────────────

    @Test
    void generateAndSave_agentReturnsValidJson_savesCalled() {
        stubDistributor();
        when(recommendationAgent.generateRecommendations(any())).thenReturn(VALID_JSON_ARRAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Recommendation>> captor =
                ArgumentCaptor.forClass(List.class);

        service.generateAndSave(DISTRIBUTOR_ID);

        verify(recommendationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
    }

    // ── Test 3: Agent throws → empty list, saveAll NOT called ────────────────

    @Test
    void generateAndSave_agentThrowsException_returnsEmptyList() {
        stubDistributor();
        when(recommendationAgent.generateRecommendations(any()))
                .thenThrow(new RuntimeException("RBS AI unavailable"));

        List<Recommendation> result = service.generateAndSave(DISTRIBUTOR_ID);

        assertThat(result).isEmpty();
        verify(recommendationRepository, never()).saveAll(anyList());
    }

    // ── Test 4: Agent returns empty array → empty list, saveAll NOT called ───

    @Test
    void generateAndSave_agentReturnsEmptyArray_returnsEmptyList() {
        stubDistributor();
        when(recommendationAgent.generateRecommendations(any())).thenReturn("[]");

        List<Recommendation> result = service.generateAndSave(DISTRIBUTOR_ID);

        // When the LLM returns an empty array the service logs a warning and
        // returns Collections.emptyList() without calling saveAll.
        assertThat(result).isEmpty();
        verify(recommendationRepository, never()).saveAll(anyList());
    }

    // ── Test 5: Markdown-fenced JSON → correctly parsed ──────────────────────

    @Test
    void generateAndSave_agentReturnsJsonWrappedInBackticks_parsedCorrectly() {
        stubDistributor();

        String fencedResponse = """
                ```json
                [
                  {
                    "recommendationType": "PAYMENT_COLLECTION",
                    "observation": "Three merchants overdue > 30 days.",
                    "evidence": {"overdueCount": 3},
                    "recommendation": "Escalate to credit team immediately.",
                    "expectedImpact": "Recover KSh 450,000.",
                    "priority": "HIGH"
                  }
                ]
                ```
                """;

        when(recommendationAgent.generateRecommendations(any())).thenReturn(fencedResponse);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Recommendation>> captor =
                ArgumentCaptor.forClass(List.class);

        service.generateAndSave(DISTRIBUTOR_ID);

        verify(recommendationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getRecommendationType())
                .isEqualTo(RecommendationType.PAYMENT_COLLECTION);
    }

    // ── Test 6: Unknown recommendationType → defaults to SALES_TREND ─────────

    @Test
    void generateAndSave_unknownRecommendationType_fallsBackToSalesTrend() {
        stubDistributor();

        String jsonWithInvalidType = """
                [
                  {
                    "recommendationType": "INVALID_TYPE_XYZ",
                    "observation": "Some observation.",
                    "evidence": {},
                    "recommendation": "Some recommendation.",
                    "expectedImpact": "Some impact.",
                    "priority": "MEDIUM"
                  }
                ]
                """;

        when(recommendationAgent.generateRecommendations(any())).thenReturn(jsonWithInvalidType);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Recommendation>> captor =
                ArgumentCaptor.forClass(List.class);

        service.generateAndSave(DISTRIBUTOR_ID);

        verify(recommendationRepository).saveAll(captor.capture());
        List<Recommendation> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRecommendationType())
                .isEqualTo(RecommendationType.SALES_TREND);
    }

    // ── Test 7: Unknown priority → defaults to MEDIUM ────────────────────────

    @Test
    void generateAndSave_unknownPriority_fallsBackToMedium() {
        stubDistributor();

        String jsonWithInvalidPriority = """
                [
                  {
                    "recommendationType": "INVENTORY_OPTIMIZATION",
                    "observation": "Stock of product X is critically low.",
                    "evidence": {"currentStock": 5},
                    "recommendation": "Reorder product X immediately.",
                    "expectedImpact": "Prevent stockout within 3 days.",
                    "priority": "URGENT"
                  }
                ]
                """;

        when(recommendationAgent.generateRecommendations(any())).thenReturn(jsonWithInvalidPriority);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Recommendation>> captor =
                ArgumentCaptor.forClass(List.class);

        service.generateAndSave(DISTRIBUTOR_ID);

        verify(recommendationRepository).saveAll(captor.capture());
        List<Recommendation> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPriority())
                .isEqualTo(RecommendationPriority.MEDIUM);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Configure the distributor repository to return a non-null distributor
     * mock for the shared {@link #DISTRIBUTOR_ID}.
     */
    private void stubDistributor() {
        Distributor distributor = mock(Distributor.class);
        when(distributor.getName()).thenReturn("Test Distributor Ltd");
        when(distributorRepository.findById(DISTRIBUTOR_ID))
                .thenReturn(Optional.of(distributor));
    }
}
