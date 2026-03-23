package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.ChurnPrediction;
import com.zuqi.domain.customer.Customer;
import com.zuqi.repository.ChurnPredictionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChurnRiskTool}.
 *
 * Covers: happy path with CRITICAL and HIGH tier customers, empty at-risk list,
 * invalid UUID, and repository exception.
 */
@ExtendWith(MockitoExtension.class)
class ChurnRiskToolTest {

    @Mock private ChurnPredictionRepository churnPredictionRepository;

    @InjectMocks
    private ChurnRiskTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void happyPath_returnsJsonWithCorrectToolName() {
        UUID distId = UUID.randomUUID();

        ChurnPrediction critical = mockPrediction(
                "CRITICAL", "Zawadi Mini Mart", 0.91, 45,
                "NO_ORDERS_30D", "URGENT_VISIT");
        ChurnPrediction high = mockPrediction(
                "HIGH", "Mwangi Kiosk", 0.72, 25,
                "LOW_ORDER_FREQUENCY", "CALL_AND_OFFER_CREDIT");

        when(churnPredictionRepository.findAtRiskCustomers(eq(distId), eq(0.6)))
                .thenReturn(List.of(critical, high));

        String result = tool.getChurnRisk(distId.toString());

        assertThat(result).contains("\"tool\": \"ChurnRisk\"");
        assertThat(result).contains("\"atRiskCount\": 2");
        assertThat(result).contains("\"critical\": 1");
        assertThat(result).contains("\"high\": 1");
        assertThat(result).contains("\"customers\"");
        assertThat(result).contains("Zawadi Mini Mart");
        assertThat(result).contains("URGENT_VISIT");
    }

    @Test
    void emptyList_returnsValidJsonWithZeroCounts() {
        UUID distId = UUID.randomUUID();
        when(churnPredictionRepository.findAtRiskCustomers(any(UUID.class), anyDouble()))
                .thenReturn(List.of());

        String result = tool.getChurnRisk(distId.toString());

        assertThat(result).contains("\"tool\": \"ChurnRisk\"");
        assertThat(result).contains("\"atRiskCount\": 0");
        assertThat(result).contains("\"critical\": 0");
        assertThat(result).contains("\"high\": 0");
        assertThat(result).contains("\"customers\": []");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void invalidUuid_returnsErrorJson() {
        String result = tool.getChurnRisk("not-uuid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(churnPredictionRepository);
    }

    @Test
    void repositoryThrows_returnsErrorJson() {
        when(churnPredictionRepository.findAtRiskCustomers(any(UUID.class), anyDouble()))
                .thenThrow(new RuntimeException("Lock timeout"));

        String result = tool.getChurnRisk(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private ChurnPrediction mockPrediction(String tier, String businessName,
                                            double probability, int daysSinceLastOrder,
                                            String topFactor, String action) {
        Customer customer = mock(Customer.class);
        when(customer.getBusinessName()).thenReturn(businessName);

        ChurnPrediction p = mock(ChurnPrediction.class);
        when(p.getRiskTier()).thenReturn(tier);
        when(p.getCustomer()).thenReturn(customer);
        when(p.getChurnProbability()).thenReturn(probability);
        when(p.getDaysSinceLastOrder()).thenReturn(daysSinceLastOrder);
        when(p.getTopChurnFactor()).thenReturn(topFactor);
        when(p.getRecommendedAction()).thenReturn(action);
        return p;
    }
}
