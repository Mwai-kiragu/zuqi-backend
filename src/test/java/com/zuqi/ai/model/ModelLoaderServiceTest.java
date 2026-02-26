package com.zuqi.ai.model;

import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.ModelStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ModelLoaderService.
 *
 * Note: @Cacheable annotations are not active in Mockito unit tests (no Spring
 * context), so method bodies execute directly — ideal for testing error paths
 * and delegation logic. Happy-path deserialization of real Tribuo models is
 * covered by integration tests (TrainInitialModelsManualTest).
 */
@ExtendWith(MockitoExtension.class)
class ModelLoaderServiceTest {

    @Mock
    private ModelRegistry modelRegistry;

    @InjectMocks
    private ModelLoaderService modelLoaderService;

    private static final String MODEL_NAME = "demand_forecaster";

    // -------------------------------------------------------------------------
    // loadModel(name) — active model lookup
    // -------------------------------------------------------------------------

    @Test
    void loadModel_shouldThrowModelNotFoundException_whenNoActiveModelExists() {
        when(modelRegistry.getActiveModel(MODEL_NAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modelLoaderService.loadModel(MODEL_NAME))
                .isInstanceOf(ModelLoader.ModelNotFoundException.class)
                .hasMessageContaining(MODEL_NAME);
    }

    @Test
    void loadModel_shouldThrowModelLoadException_whenModelBinaryIsNull() {
        AIModelRegistry model = buildModel(MODEL_NAME, 1, ModelStatus.ACTIVE, null);
        when(modelRegistry.getActiveModel(MODEL_NAME)).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> modelLoaderService.loadModel(MODEL_NAME))
                .isInstanceOf(ModelLoader.ModelLoadException.class)
                .hasMessageContaining(MODEL_NAME);
    }

    @Test
    void loadModel_shouldThrowModelLoadException_whenModelBinaryIsEmpty() {
        AIModelRegistry model = buildModel(MODEL_NAME, 1, ModelStatus.ACTIVE, new byte[0]);
        when(modelRegistry.getActiveModel(MODEL_NAME)).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> modelLoaderService.loadModel(MODEL_NAME))
                .isInstanceOf(ModelLoader.ModelLoadException.class)
                .hasMessageContaining(MODEL_NAME);
    }

    @Test
    void loadModel_shouldThrowModelLoadException_whenBinaryIsCorrupted() {
        AIModelRegistry model = buildModel(MODEL_NAME, 1, ModelStatus.ACTIVE,
                new byte[]{0x00, 0x01, 0x02}); // invalid serialization bytes
        when(modelRegistry.getActiveModel(MODEL_NAME)).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> modelLoaderService.loadModel(MODEL_NAME))
                .isInstanceOf(ModelLoader.ModelLoadException.class);
    }

    // -------------------------------------------------------------------------
    // loadModel(name, version) — versioned lookup
    // -------------------------------------------------------------------------

    @Test
    void loadModel_withVersion_shouldThrowModelNotFoundException_whenModelDoesNotExist() {
        when(modelRegistry.getModel(MODEL_NAME, 3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modelLoaderService.loadModel(MODEL_NAME, 3))
                .isInstanceOf(ModelLoader.ModelNotFoundException.class)
                .hasMessageContaining(MODEL_NAME);
    }

    @Test
    void loadModel_withVersion_shouldThrowModelNotFoundException_whenModelIsInTrainingStatus() {
        AIModelRegistry trainingModel = buildModel(MODEL_NAME, 1, ModelStatus.TRAINING, new byte[]{1});
        when(modelRegistry.getModel(MODEL_NAME, 1)).thenReturn(Optional.of(trainingModel));

        assertThatThrownBy(() -> modelLoaderService.loadModel(MODEL_NAME, 1))
                .isInstanceOf(ModelLoader.ModelNotFoundException.class)
                .hasMessageContaining("TRAINING");
    }

    @Test
    void loadModel_withVersion_shouldThrowModelLoadException_whenBinaryIsNull() {
        AIModelRegistry model = buildModel(MODEL_NAME, 2, ModelStatus.EVALUATING, null);
        when(modelRegistry.getModel(MODEL_NAME, 2)).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> modelLoaderService.loadModel(MODEL_NAME, 2))
                .isInstanceOf(ModelLoader.ModelLoadException.class);
    }

    // -------------------------------------------------------------------------
    // loadModel — happy path (covered by TrainInitialModelsManualTest integration test;
    // deserializing a real Tribuo Model requires a full Spring context)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // evictModel
    // -------------------------------------------------------------------------

    @Test
    void evictModel_shouldCompleteWithoutException() {
        // @CacheEvict is not active in unit tests; just verify no exception thrown
        modelLoaderService.evictModel(MODEL_NAME);
        // No interaction with modelRegistry expected
        verifyNoInteractions(modelRegistry);
    }

    // -------------------------------------------------------------------------
    // warmUpCache
    // -------------------------------------------------------------------------

    @Test
    void warmUpCache_shouldCompleteWithoutException_whenNoActiveModels() {
        when(modelRegistry.getModelsByStatus(ModelStatus.ACTIVE)).thenReturn(List.of());

        modelLoaderService.warmUpCache();

        verify(modelRegistry).getModelsByStatus(ModelStatus.ACTIVE);
    }

    @Test
    void warmUpCache_shouldAttemptLoadForEachActiveModel() {
        AIModelRegistry m1 = buildModel("credit_classifier", 1, ModelStatus.ACTIVE, null);
        AIModelRegistry m2 = buildModel("demand_forecaster", 2, ModelStatus.ACTIVE, null);

        when(modelRegistry.getModelsByStatus(ModelStatus.ACTIVE)).thenReturn(List.of(m1, m2));
        // loadModel will throw ModelLoadException (null binary) — warmUpCache must not propagate it
        when(modelRegistry.getActiveModel("credit_classifier")).thenReturn(Optional.of(m1));
        when(modelRegistry.getActiveModel("demand_forecaster")).thenReturn(Optional.of(m2));

        // Should not throw despite individual model load failures
        modelLoaderService.warmUpCache();

        verify(modelRegistry).getModelsByStatus(ModelStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // getCacheStats
    // -------------------------------------------------------------------------

    @Test
    void getCacheStats_shouldReturnZeroStats_onFreshInstance() {
        ModelLoader.CacheStats stats = modelLoaderService.getCacheStats();

        assertThat(stats.hits()).isZero();
        assertThat(stats.misses()).isZero();
        assertThat(stats.size()).isZero();
        assertThat(stats.hitRate()).isZero();
    }

    @Test
    void getCacheStats_shouldRecordMisses_afterFailedLoads() {
        when(modelRegistry.getActiveModel(MODEL_NAME)).thenReturn(Optional.empty());

        // Two failed load attempts — each increments miss counter
        assertThatThrownBy(() -> modelLoaderService.loadModel(MODEL_NAME))
                .isInstanceOf(ModelLoader.ModelNotFoundException.class);
        assertThatThrownBy(() -> modelLoaderService.loadModel(MODEL_NAME))
                .isInstanceOf(ModelLoader.ModelNotFoundException.class);

        ModelLoader.CacheStats stats = modelLoaderService.getCacheStats();
        assertThat(stats.misses()).isEqualTo(2);
        assertThat(stats.hits()).isZero();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AIModelRegistry buildModel(String name, int version, ModelStatus status, byte[] binary) {
        return AIModelRegistry.builder()
                .id(UUID.randomUUID())
                .modelName(name)
                .modelVersion(version)
                .algorithm("xgboost")
                .status(status)
                .modelBinary(binary)
                .build();
    }
}
