package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.SupplierRiskScore;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.repository.SupplierRiskScoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SupplierRiskTool}.
 *
 * Covers: happy path with mixed risk tiers via paged repository, empty page,
 * invalid UUID, and repository exception.
 */
@ExtendWith(MockitoExtension.class)
class SupplierRiskToolTest {

    @Mock private SupplierRiskScoreRepository supplierRiskScoreRepository;

    @InjectMocks
    private SupplierRiskTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void happyPath_returnsJsonWithCorrectToolName() {
        UUID distId = UUID.randomUUID();

        SupplierRiskScore preferred = mockScore("Bidco Africa",    "PREFERRED", 88.0, 92.0, 90.0, 85.0, 88.0);
        SupplierRiskScore atRisk    = mockScore("Local Depot Ltd", "AT_RISK",   32.0, 40.0, 35.0, 28.0, 30.0);

        Page<SupplierRiskScore> page = mock(Page.class);
        when(page.getContent()).thenReturn(List.of(preferred, atRisk));
        when(supplierRiskScoreRepository.findByDistributorId(eq(distId), any(Pageable.class)))
                .thenReturn(page);

        String result = tool.getSupplierRisk(distId.toString());

        assertThat(result).contains("\"tool\": \"SupplierRisk\"");
        assertThat(result).contains("\"preferred\": 1");
        assertThat(result).contains("\"atRisk\": 1");
        assertThat(result).contains("\"critical\": 0");
        assertThat(result).contains("\"suppliers\"");
        assertThat(result).contains("Bidco Africa");
        assertThat(result).contains("PREFERRED");
    }

    @SuppressWarnings("unchecked")
    @Test
    void emptyList_returnsValidJsonWithZeroCounts() {
        UUID distId = UUID.randomUUID();

        Page<SupplierRiskScore> page = mock(Page.class);
        when(page.getContent()).thenReturn(List.of());
        when(supplierRiskScoreRepository.findByDistributorId(any(UUID.class), any(Pageable.class)))
                .thenReturn(page);

        String result = tool.getSupplierRisk(distId.toString());

        assertThat(result).contains("\"tool\": \"SupplierRisk\"");
        assertThat(result).contains("\"preferred\": 0");
        assertThat(result).contains("\"atRisk\": 0");
        assertThat(result).contains("\"critical\": 0");
        assertThat(result).contains("\"suppliers\": []");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void invalidUuid_returnsErrorJson() {
        String result = tool.getSupplierRisk("not-uuid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(supplierRiskScoreRepository);
    }

    @Test
    void repositoryThrows_returnsErrorJson() {
        when(supplierRiskScoreRepository.findByDistributorId(any(UUID.class), any(Pageable.class)))
                .thenThrow(new RuntimeException("Network error"));

        String result = tool.getSupplierRisk(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private SupplierRiskScore mockScore(String supplierName, String tier, double riskScore,
                                        double delivery, double quality,
                                        double priceConsistency, double responsiveness) {
        Supplier supplier = mock(Supplier.class);
        when(supplier.getName()).thenReturn(supplierName);

        SupplierRiskScore s = mock(SupplierRiskScore.class);
        when(s.getSupplier()).thenReturn(supplier);
        when(s.getRiskTier()).thenReturn(tier);
        when(s.getRiskScore()).thenReturn(riskScore);
        when(s.getDeliveryReliabilityScore()).thenReturn(delivery);
        when(s.getQualityScore()).thenReturn(quality);
        when(s.getPriceConsistencyScore()).thenReturn(priceConsistency);
        when(s.getResponsivenessScore()).thenReturn(responsiveness);
        return s;
    }
}
