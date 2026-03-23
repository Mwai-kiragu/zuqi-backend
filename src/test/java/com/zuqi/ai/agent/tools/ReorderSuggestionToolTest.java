package com.zuqi.ai.agent.tools;

import com.zuqi.domain.ai.ReorderSuggestion;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.ReorderSuggestionRepository;
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
 * Unit tests for {@link ReorderSuggestionTool}.
 *
 * Covers: happy path with two PENDING suggestions, empty list,
 * invalid UUID, and repository exception.
 */
@ExtendWith(MockitoExtension.class)
class ReorderSuggestionToolTest {

    @Mock private ReorderSuggestionRepository reorderSuggestionRepository;

    @InjectMocks
    private ReorderSuggestionTool tool;

    // ── happy paths ───────────────────────────────────────────────────────

    @Test
    void happyPath_returnsJsonWithCorrectToolName() {
        UUID distId = UUID.randomUUID();

        ReorderSuggestion s1 = mockSuggestion("Sunflower Oil 1L", "Nairobi WH",  500.0, 100.0, 50.0, 480.0, 7.5, 3.0, 0.85);
        ReorderSuggestion s2 = mockSuggestion("Wheat Flour 2kg",  "Mombasa WH", 1200.0, 200.0, 80.0, 950.0, 3.2, 5.0, 0.78);

        when(reorderSuggestionRepository.findByDistributorIdAndStatus(eq(distId), eq("PENDING")))
                .thenReturn(List.of(s1, s2));

        String result = tool.getReorderSuggestions(distId.toString());

        assertThat(result).contains("\"tool\": \"ReorderSuggestions\"");
        assertThat(result).contains("\"pendingCount\": 2");
        assertThat(result).contains("\"suggestions\"");
        assertThat(result).contains("Sunflower Oil 1L");
        assertThat(result).contains("Nairobi WH");
    }

    @Test
    void emptyList_returnsValidJsonWithZeroCounts() {
        UUID distId = UUID.randomUUID();
        when(reorderSuggestionRepository.findByDistributorIdAndStatus(any(UUID.class), anyString()))
                .thenReturn(List.of());

        String result = tool.getReorderSuggestions(distId.toString());

        assertThat(result).contains("\"tool\": \"ReorderSuggestions\"");
        assertThat(result).contains("\"pendingCount\": 0");
        assertThat(result).contains("\"suggestions\": []");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void invalidUuid_returnsErrorJson() {
        String result = tool.getReorderSuggestions("not-uuid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(reorderSuggestionRepository);
    }

    @Test
    void repositoryThrows_returnsErrorJson() {
        when(reorderSuggestionRepository.findByDistributorIdAndStatus(any(UUID.class), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        String result = tool.getReorderSuggestions(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private ReorderSuggestion mockSuggestion(String productName, String warehouseName,
                                              double suggestedQty, double reorderPoint,
                                              double safetyStock, double eoq,
                                              double daysOfSupply, double leadTime,
                                              double confidence) {
        Product product = mock(Product.class);
        when(product.getName()).thenReturn(productName);

        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getName()).thenReturn(warehouseName);

        ReorderSuggestion s = mock(ReorderSuggestion.class);
        when(s.getProduct()).thenReturn(product);
        when(s.getWarehouse()).thenReturn(warehouse);
        when(s.getSuggestedQty()).thenReturn(suggestedQty);
        when(s.getReorderPoint()).thenReturn(reorderPoint);
        when(s.getSafetyStock()).thenReturn(safetyStock);
        when(s.getEconomicOrderQty()).thenReturn(eoq);
        when(s.getDaysOfSupplyRemaining()).thenReturn(daysOfSupply);
        when(s.getLeadTimeDays()).thenReturn(leadTime);
        when(s.getConfidenceScore()).thenReturn(confidence);
        return s;
    }
}
