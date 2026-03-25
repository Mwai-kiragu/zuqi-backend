package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.PriceTrend;
import com.zuqi.domain.product.Product;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.repository.PriceTrendRepository;
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
 * Unit tests for {@link PriceTrendTool}.
 *
 * Covers: happy path with INCREASING and DECREASING trends, empty list,
 * invalid UUID, and repository exception.
 */
@ExtendWith(MockitoExtension.class)
class PriceTrendToolTest {

    @Mock private PriceTrendRepository priceTrendRepository;

    @InjectMocks
    private PriceTrendTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void happyPath_returnsJsonWithCorrectToolName() {
        UUID distId = UUID.randomUUID();

        PriceTrend increasing = mockTrend("INCREASING", "Bidco Africa",  "Sunflower Oil 1L",  12.5, 145.0, 130.0, 0.08);
        PriceTrend decreasing = mockTrend("DECREASING", "Unga Limited",  "Wheat Flour 2kg",   -5.3, 185.0, 192.0, 0.04);

        when(priceTrendRepository.findByDistributorId(eq(distId)))
                .thenReturn(List.of(increasing, decreasing));

        String result = tool.getPriceTrends(distId.toString());

        assertThat(result).contains("\"tool\": \"PriceTrends\"");
        assertThat(result).contains("\"increasing\": 1");
        assertThat(result).contains("\"decreasing\": 1");
        assertThat(result).contains("\"stable\": 0");
        assertThat(result).contains("\"trends\"");
        assertThat(result).contains("Bidco Africa");
        assertThat(result).contains("Sunflower Oil 1L");
        assertThat(result).contains("INCREASING");
    }

    @Test
    void emptyList_returnsValidJsonWithZeroCounts() {
        UUID distId = UUID.randomUUID();
        when(priceTrendRepository.findByDistributorId(any(UUID.class)))
                .thenReturn(List.of());

        String result = tool.getPriceTrends(distId.toString());

        assertThat(result).contains("\"tool\": \"PriceTrends\"");
        assertThat(result).contains("\"increasing\": 0");
        assertThat(result).contains("\"decreasing\": 0");
        assertThat(result).contains("\"stable\": 0");
        assertThat(result).contains("\"trends\": []");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void invalidUuid_returnsErrorJson() {
        String result = tool.getPriceTrends("not-uuid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(priceTrendRepository);
    }

    @Test
    void repositoryThrows_returnsErrorJson() {
        when(priceTrendRepository.findByDistributorId(any(UUID.class)))
                .thenThrow(new RuntimeException("Datasource unavailable"));

        String result = tool.getPriceTrends(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private PriceTrend mockTrend(String direction, String supplierName, String productName,
                                  double pctChange3m, double currentPrice,
                                  double marketAvg, double volatility) {
        Supplier supplier = mock(Supplier.class);
        when(supplier.getName()).thenReturn(supplierName);

        Product product = mock(Product.class);
        when(product.getName()).thenReturn(productName);

        PriceTrend t = mock(PriceTrend.class);
        when(t.getTrendDirection()).thenReturn(direction);
        when(t.getSupplier()).thenReturn(supplier);
        when(t.getProduct()).thenReturn(product);
        when(t.getPctChange3m()).thenReturn(pctChange3m);
        when(t.getCurrentUnitPrice()).thenReturn(currentPrice);
        when(t.getMarketAvgPrice()).thenReturn(marketAvg);
        when(t.getPriceVolatility()).thenReturn(volatility);
        return t;
    }
}
