package com.zuqi.api.controller;

import com.zuqi.ai.prediction.PredictionAlertService;
import com.zuqi.ai.prediction.RepPerformancePredictor;
import com.zuqi.ai.prediction.StockoutPredictor;
import com.zuqi.ai.prediction.StockoutTrainingPipeline;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.DataPhase;
import com.zuqi.domain.inventory.Stock;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.StockRepository;
import com.zuqi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiPredictionController}.
 *
 * Covers: stockout batch, rep-performance batch, single rep, error paths.
 */
@ExtendWith(MockitoExtension.class)
class AiPredictionControllerTest {

    @Mock private StockoutPredictor        stockoutPredictor;
    @Mock private RepPerformancePredictor  repPerformancePredictor;
    @Mock private PredictionAlertService   predictionAlertService;
    @Mock private StockRepository          stockRepository;
    @Mock private UserRepository           userRepository;
    @Mock private StockoutTrainingPipeline stockoutTrainingPipeline;
    @Mock private DataPhaseTracker         dataPhaseTracker;

    @InjectMocks
    private AiPredictionController controller;

    // ── GET /stockout/{warehouseId} ───────────────────────────────────────

    @Test
    void getStockoutPredictions_returns200WithCounts() {
        UUID warehouseId   = UUID.randomUUID();
        UUID distributorId = UUID.randomUUID();
        UUID productId     = UUID.randomUUID();

        Stock stock = mockStock(productId);
        when(stockRepository.findByWarehouseId(eq(warehouseId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stock)));
        when(dataPhaseTracker.getPhase(anyString(), any())).thenReturn(DataPhase.SYNTHETIC);

        StockoutPredictor.StockoutResult result = new StockoutPredictor.StockoutResult(
                warehouseId, productId, 0.75, "STOCKOUT", 3.0, 100.0, 10.0, "STABLE", 0.0, "stockout_predictor-v1");
        when(stockoutPredictor.predict(warehouseId, productId)).thenReturn(result);

        ResponseEntity<?> response = controller.getStockoutPredictions(warehouseId, distributorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(predictionAlertService).evaluateStockoutAndAlert(result, distributorId);
    }

    @Test
    void getStockoutPredictions_whenNoProducts_returnsEmptyBatch() {
        UUID warehouseId   = UUID.randomUUID();
        UUID distributorId = UUID.randomUUID();

        when(stockRepository.findByWarehouseId(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(dataPhaseTracker.getPhase(anyString(), any())).thenReturn(DataPhase.SYNTHETIC);

        ResponseEntity<?> response = controller.getStockoutPredictions(warehouseId, distributorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(stockoutPredictor);
    }

    @Test
    void getStockoutPredictions_whenServiceThrows_returns500() {
        when(stockRepository.findByWarehouseId(any(), any(Pageable.class)))
                .thenThrow(new RuntimeException("DB error"));

        ResponseEntity<?> response = controller.getStockoutPredictions(
                UUID.randomUUID(), UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── GET /rep-performance ──────────────────────────────────────────────

    @Test
    void getAllRepPerformance_whenNoReps_returnsEmptyBatch() {
        UUID distributorId = UUID.randomUUID();
        when(userRepository.findByDistributorIdAndActiveTrue(distributorId))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.getAllRepPerformance(distributorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(repPerformancePredictor);
    }

    @Test
    void getAllRepPerformance_whenServiceThrows_returns500() {
        when(userRepository.findByDistributorIdAndActiveTrue(any()))
                .thenThrow(new RuntimeException("Query failed"));

        ResponseEntity<?> response = controller.getAllRepPerformance(UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── GET /rep-performance/{repId} ──────────────────────────────────────

    @Test
    void getSingleRepPerformance_returns200() {
        UUID repId         = UUID.randomUUID();
        UUID distributorId = UUID.randomUUID();

        RepPerformancePredictor.RepPerformanceResult result =
                new RepPerformancePredictor.RepPerformanceResult(
                        repId, 72.0, "GOOD", "rep_performance_predictor-v1");
        when(repPerformancePredictor.predict(repId)).thenReturn(result);

        ResponseEntity<?> response = controller.getSingleRepPerformance(repId, distributorId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(predictionAlertService).evaluateRepPerformanceAndAlert(result, distributorId);
    }

    @Test
    void getSingleRepPerformance_whenServiceThrows_returns500() {
        when(repPerformancePredictor.predict(any()))
                .thenThrow(new RuntimeException("Model unavailable"));

        ResponseEntity<?> response = controller.getSingleRepPerformance(
                UUID.randomUUID(), UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Stock mockStock(UUID productId) {
        Product product = new Product();
        product.setId(productId);
        product.setName("Test Product");

        Warehouse warehouse = new Warehouse();
        warehouse.setName("Test Warehouse");

        Stock stock = new Stock();
        stock.setProduct(product);
        stock.setWarehouse(warehouse);
        return stock;
    }
}
