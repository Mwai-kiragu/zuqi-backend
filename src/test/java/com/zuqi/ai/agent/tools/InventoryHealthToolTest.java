package com.zuqi.ai.agent.tools;

import com.zuqi.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InventoryHealthTool}.
 *
 * Covers health status derivation logic (HEALTHY, WARNING, CRITICAL),
 * invalid UUID handling, and repository exceptions.
 */
@ExtendWith(MockitoExtension.class)
class InventoryHealthToolTest {

    @Mock private StockRepository stockRepository;

    @InjectMocks
    private InventoryHealthTool tool;

    // ── health status logic ───────────────────────────────────────────────

    @Test
    void getInventoryHealth_whenZeroProblems_returnsHealthy() {
        UUID distributorId = UUID.randomUUID();

        when(stockRepository.countLowStockByDistributorId(distributorId)).thenReturn(0L);
        when(stockRepository.countOutOfStockByDistributorId(distributorId)).thenReturn(0L);
        when(stockRepository.findLowStockByDistributorId(eq(distributorId), any()))
                .thenReturn(new PageImpl<>(List.of()));

        String result = tool.getInventoryHealth(distributorId.toString());

        assertThat(result).contains("\"tool\": \"InventoryHealth\"");
        assertThat(result).contains("\"healthStatus\": \"HEALTHY\"");
        assertThat(result).contains("\"outOfStock\": 0");
    }

    @Test
    void getInventoryHealth_whenOutOfStockAndHighLowStock_returnsCritical() {
        UUID distributorId = UUID.randomUUID();

        when(stockRepository.countLowStockByDistributorId(distributorId)).thenReturn(8L);
        when(stockRepository.countOutOfStockByDistributorId(distributorId)).thenReturn(3L);
        when(stockRepository.findLowStockByDistributorId(eq(distributorId), any()))
                .thenReturn(new PageImpl<>(List.of()));

        String result = tool.getInventoryHealth(distributorId.toString());

        assertThat(result).contains("\"healthStatus\": \"CRITICAL\"");
    }

    @Test
    void getInventoryHealth_whenOutOfStockOnly_returnsWarning() {
        UUID distributorId = UUID.randomUUID();

        when(stockRepository.countLowStockByDistributorId(distributorId)).thenReturn(1L);
        when(stockRepository.countOutOfStockByDistributorId(distributorId)).thenReturn(1L);
        when(stockRepository.findLowStockByDistributorId(eq(distributorId), any()))
                .thenReturn(new PageImpl<>(List.of()));

        String result = tool.getInventoryHealth(distributorId.toString());

        assertThat(result).contains("\"healthStatus\": \"WARNING\"");
    }

    // ── error paths ───────────────────────────────────────────────────────

    @Test
    void getInventoryHealth_whenInvalidUuid_returnsErrorJson() {
        String result = tool.getInventoryHealth("bad-uuid");

        assertThat(result).contains("\"error\"");
        verifyNoInteractions(stockRepository);
    }

    @Test
    void getInventoryHealth_whenRepositoryThrows_returnsErrorJson() {
        when(stockRepository.countLowStockByDistributorId(any()))
                .thenThrow(new RuntimeException("Connection refused"));

        String result = tool.getInventoryHealth(UUID.randomUUID().toString());

        assertThat(result).contains("\"error\"");
    }
}
