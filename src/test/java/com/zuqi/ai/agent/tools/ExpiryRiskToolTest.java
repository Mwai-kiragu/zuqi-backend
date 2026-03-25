package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.ExpiryRiskScore;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.ExpiryRiskScoreRepository;
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
 * Unit tests for {@link ExpiryRiskTool}.
 *
 * Covers: happy path with mixed risk tiers, empty batch list,
 * invalid UUID, and repository exception.
 */
@ExtendWith(MockitoExtension.class)
class ExpiryRiskToolTest {

    @Mock private ExpiryRiskScoreRepository expiryRiskScoreRepository;

    @InjectMocks
    private ExpiryRiskTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void happyPath_returnsJsonWithCorrectToolName() {
        UUID distId = UUID.randomUUID();

        ExpiryRiskScore critical = mockScore("CRITICAL", "Maziwa Safi", "Nairobi WH", "BATCH-001", 3, 0.92, "QUARANTINE", 30.0);
        ExpiryRiskScore high     = mockScore("HIGH",     "UHT Milk",    "Mombasa WH", "BATCH-002", 8, 0.75, "DISCOUNT",   15.0);

        when(expiryRiskScoreRepository.findHighRiskByDistributor(eq(distId), eq(0.3)))
                .thenReturn(List.of(critical, high));

        String result = tool.getExpiryRisks(distId.toString());

        assertThat(result).contains("\"tool\": \"ExpiryRisk\"");
        assertThat(result).contains("\"critical\": 1");
        assertThat(result).contains("\"high\": 1");
        assertThat(result).contains("\"moderate\": 0");
        assertThat(result).contains("\"batches\"");
        assertThat(result).contains("Maziwa Safi");
        assertThat(result).contains("BATCH-001");
        assertThat(result).contains("QUARANTINE");
    }

    @Test
    void emptyList_returnsValidJsonWithZeroCounts() {
        UUID distId = UUID.randomUUID();
        when(expiryRiskScoreRepository.findHighRiskByDistributor(any(UUID.class), anyDouble()))
                .thenReturn(List.of());

        String result = tool.getExpiryRisks(distId.toString());

        assertThat(result).contains("\"tool\": \"ExpiryRisk\"");
        assertThat(result).contains("\"critical\": 0");
        assertThat(result).contains("\"high\": 0");
        assertThat(result).contains("\"moderate\": 0");
        assertThat(result).contains("\"batches\": []");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void invalidUuid_returnsErrorJson() {
        String result = tool.getExpiryRisks("not-uuid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(expiryRiskScoreRepository);
    }

    @Test
    void repositoryThrows_returnsErrorJson() {
        when(expiryRiskScoreRepository.findHighRiskByDistributor(any(UUID.class), anyDouble()))
                .thenThrow(new RuntimeException("DB timeout"));

        String result = tool.getExpiryRisks(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private ExpiryRiskScore mockScore(String tier, String productName, String warehouseName,
                                      String batch, int daysToExpiry, double riskScore,
                                      String action, double discountPct) {
        Product product = mock(Product.class);
        when(product.getName()).thenReturn(productName);

        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getName()).thenReturn(warehouseName);

        ExpiryRiskScore score = mock(ExpiryRiskScore.class);
        when(score.getRiskTier()).thenReturn(tier);
        when(score.getProduct()).thenReturn(product);
        when(score.getWarehouse()).thenReturn(warehouse);
        when(score.getBatchNumber()).thenReturn(batch);
        when(score.getDaysToExpiry()).thenReturn(daysToExpiry);
        when(score.getRiskScore()).thenReturn(riskScore);
        when(score.getRecommendedAction()).thenReturn(action);
        when(score.getDiscountSuggestionPct()).thenReturn(discountPct);
        return score;
    }
}
