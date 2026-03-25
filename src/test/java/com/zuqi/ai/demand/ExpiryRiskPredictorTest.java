package com.zuqi.ai.demand;

import com.zuqi.ai.feature.ExpiryFeatureServiceImpl;
import com.zuqi.ai.feature.ExpiryFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.DataPhase;
import com.zuqi.domain.ai.ExpiryRiskScore;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.inventory.ProductBatch;
import com.zuqi.domain.inventory.Warehouse;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.ExpiryRiskScoreRepository;
import com.zuqi.repository.ProductBatchRepository;
import com.zuqi.repository.ProductRepository;
import com.zuqi.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpiryRiskPredictorTest {

    @Mock private ExpiryFeatureServiceImpl featureService;
    @Mock private ExpiryRiskFeatureBuilder featureBuilder;
    @Mock private ModelLoaderService modelLoader;
    @Mock private ModelPhaseService phaseService;
    @Mock private DataPhaseTracker phaseTracker;
    @Mock private ModelRegistry modelRegistry;
    @Mock private ExpiryRiskScoreRepository expiryRiskScoreRepository;
    @Mock private DistributorRepository distributorRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductBatchRepository productBatchRepository;

    @InjectMocks
    private ExpiryRiskPredictor predictor;

    private ExpiryFeatures makeFeatures(int daysToExpiry, double stockQty, double dailyRate) {
        return new ExpiryFeatures(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "BATCH-T1",
                LocalDate.now().plusDays(daysToExpiry),
                daysToExpiry, stockQty, dailyRate,
                dailyRate > 0 ? stockQty / dailyRate : 999.0,
                dailyRate * 0.9, 12.0, 0.3, 0.4
        );
    }

    @Test
    void predict_noModel_usesHeuristicFallback_forHighSellThroughBatch() {
        UUID distId = UUID.randomUUID(), whId = UUID.randomUUID(),
             prodId = UUID.randomUUID(), batchId = UUID.randomUUID();

        // 30 days left, sells at 5/day → projected=20 days → will sell out (prob=1.0)
        ExpiryFeatures features = makeFeatures(30, 100, 5.0);

        when(featureService.computeFeatures(distId, whId, prodId, batchId)).thenReturn(features);
        when(modelLoader.loadModel(ExpiryRiskTrainingPipeline.MODEL_NAME)).thenReturn(null);
        when(phaseService.applyModifier(0.75, ExpiryRiskTrainingPipeline.MODEL_NAME)).thenReturn(0.45);
        when(phaseTracker.getPhase(ExpiryRiskTrainingPipeline.MODEL_NAME, distId)).thenReturn(DataPhase.SYNTHETIC);

        when(distributorRepository.findById(distId)).thenReturn(Optional.of(new Distributor()));
        when(warehouseRepository.findById(whId)).thenReturn(Optional.of(new Warehouse()));
        when(productRepository.findById(prodId)).thenReturn(Optional.of(new Product()));
        when(productBatchRepository.findById(batchId)).thenReturn(Optional.of(new ProductBatch()));

        ExpiryRiskScore saved = ExpiryRiskScore.builder()
                .riskScore(0.0).riskTier("NORMAL").recommendedAction("NORMAL").build();
        when(expiryRiskScoreRepository.save(any())).thenReturn(saved);

        ExpiryRiskScore result = predictor.predict(distId, whId, prodId, batchId);

        assertThat(result).isNotNull();
        assertThat(result.getRiskTier()).isEqualTo("NORMAL");
        verify(expiryRiskScoreRepository).save(any());
    }

    @Test
    void predict_noModel_highRiskBatch_returnsHighRiskScore() {
        UUID distId = UUID.randomUUID(), whId = UUID.randomUUID(),
             prodId = UUID.randomUUID(), batchId = UUID.randomUUID();

        // 5 days left, sells at 1/day → projected=100 days → will NOT sell out (prob=0.05)
        ExpiryFeatures features = makeFeatures(5, 100, 1.0);

        when(featureService.computeFeatures(distId, whId, prodId, batchId)).thenReturn(features);
        when(modelLoader.loadModel(ExpiryRiskTrainingPipeline.MODEL_NAME)).thenReturn(null);
        when(phaseService.applyModifier(0.75, ExpiryRiskTrainingPipeline.MODEL_NAME)).thenReturn(0.45);
        when(phaseTracker.getPhase(ExpiryRiskTrainingPipeline.MODEL_NAME, distId)).thenReturn(DataPhase.SYNTHETIC);

        when(distributorRepository.findById(distId)).thenReturn(Optional.of(new Distributor()));
        when(warehouseRepository.findById(whId)).thenReturn(Optional.of(new Warehouse()));
        when(productRepository.findById(prodId)).thenReturn(Optional.of(new Product()));
        when(productBatchRepository.findById(batchId)).thenReturn(Optional.of(new ProductBatch()));

        ExpiryRiskScore saved = ExpiryRiskScore.builder()
                .riskScore(0.95).riskTier("CRITICAL").recommendedAction("QUARANTINE").build();
        when(expiryRiskScoreRepository.save(any())).thenReturn(saved);

        ExpiryRiskScore result = predictor.predict(distId, whId, prodId, batchId);

        assertThat(result).isNotNull();
        assertThat(result.getRiskTier()).isEqualTo("CRITICAL");
    }

    @Test
    void predict_featureServiceThrows_propagatesException() {
        UUID distId = UUID.randomUUID(), whId = UUID.randomUUID(),
             prodId = UUID.randomUUID(), batchId = UUID.randomUUID();

        when(featureService.computeFeatures(distId, whId, prodId, batchId))
                .thenThrow(new IllegalArgumentException("Batch not found"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> predictor.predict(distId, whId, prodId, batchId));
    }
}
