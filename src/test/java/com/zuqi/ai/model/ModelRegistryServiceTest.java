package com.zuqi.ai.model;

import com.zuqi.ai.event.ModelPromotedEvent;
import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.ModelStatus;
import com.zuqi.repository.AIModelRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelRegistryServiceTest {

    @Mock
    private AIModelRegistryRepository modelRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ModelRegistryService modelRegistryService;

    private UUID modelId;

    @BeforeEach
    void setUp() {
        modelId = UUID.randomUUID();
    }

    // -------------------------------------------------------------------------
    // registerModel
    // -------------------------------------------------------------------------

    @Test
    void registerModel_shouldCreateModelInTrainingStatus_withVersionOne_whenNoExistingVersions() {
        when(modelRepository.findAllVersionsByModelName("demand_forecaster"))
                .thenReturn(List.of());
        when(modelRepository.save(any(AIModelRegistry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AIModelRegistry result = modelRegistryService.registerModel(
                "demand_forecaster", "xgboost_regression",
                Map.of("max_depth", 6), "system");

        assertThat(result.getModelName()).isEqualTo("demand_forecaster");
        assertThat(result.getAlgorithm()).isEqualTo("xgboost_regression");
        assertThat(result.getStatus()).isEqualTo(ModelStatus.TRAINING);
        assertThat(result.getModelVersion()).isEqualTo(1);
        assertThat(result.getCreatedBy()).isEqualTo("system");
        verify(modelRepository).save(any(AIModelRegistry.class));
    }

    @Test
    void registerModel_shouldIncrementVersion_whenPreviousVersionsExist() {
        AIModelRegistry existingV2 = buildModel("demand_forecaster", 2, ModelStatus.ACTIVE);
        AIModelRegistry existingV1 = buildModel("demand_forecaster", 1, ModelStatus.RETIRED);

        when(modelRepository.findAllVersionsByModelName("demand_forecaster"))
                .thenReturn(List.of(existingV2, existingV1)); // ordered desc
        when(modelRepository.save(any(AIModelRegistry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AIModelRegistry result = modelRegistryService.registerModel(
                "demand_forecaster", "xgboost_regression", Map.of(), "system");

        assertThat(result.getModelVersion()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // updateModelAfterTraining
    // -------------------------------------------------------------------------

    @Test
    void updateModelAfterTraining_shouldSetEvaluatingStatusAndStoreMetrics() {
        AIModelRegistry model = buildModel("credit_classifier", 1, ModelStatus.TRAINING);
        byte[] binary = new byte[]{1, 2, 3};
        Map<String, Object> metrics = Map.of("auc_roc", 0.82);
        Map<String, Object> features = Map.of("columns", List.of("f1", "f2"));

        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        modelRegistryService.updateModelAfterTraining(modelId, metrics, binary, features);

        assertThat(model.getStatus()).isEqualTo(ModelStatus.EVALUATING);
        assertThat(model.getPerformanceMetrics()).isEqualTo(metrics);
        assertThat(model.getModelBinary()).isEqualTo(binary);
        assertThat(model.getModelSizeBytes()).isEqualTo(3L);
        verify(modelRepository).save(model);
    }

    @Test
    void updateModelAfterTraining_shouldThrow_whenModelNotFound() {
        when(modelRepository.findById(modelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modelRegistryService.updateModelAfterTraining(
                modelId, Map.of(), new byte[0], Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(modelId.toString());
    }

    // -------------------------------------------------------------------------
    // promoteToActive
    // -------------------------------------------------------------------------

    @Test
    void promoteToActive_shouldActivateModel_andRetireExistingActiveModel() {
        AIModelRegistry candidate = buildModel("credit_classifier", 2, ModelStatus.EVALUATING);
        candidate.setId(modelId);
        AIModelRegistry currentActive = buildModel("credit_classifier", 1, ModelStatus.ACTIVE);

        when(modelRepository.findById(modelId)).thenReturn(Optional.of(candidate));
        when(modelRepository.findLatestActiveModel("credit_classifier", ModelStatus.ACTIVE))
                .thenReturn(Optional.of(currentActive));
        when(modelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AIModelRegistry result = modelRegistryService.promoteToActive(modelId);

        assertThat(result.getStatus()).isEqualTo(ModelStatus.ACTIVE);
        assertThat(result.getPromotedAt()).isNotNull();
        assertThat(currentActive.getStatus()).isEqualTo(ModelStatus.RETIRED);
        assertThat(currentActive.getRetiredAt()).isNotNull();
        verify(modelRepository, times(2)).save(any());
    }

    @Test
    void promoteToActive_shouldActivateModel_whenNoExistingActiveModel() {
        AIModelRegistry candidate = buildModel("credit_classifier", 1, ModelStatus.EVALUATING);
        candidate.setId(modelId);

        when(modelRepository.findById(modelId)).thenReturn(Optional.of(candidate));
        when(modelRepository.findLatestActiveModel("credit_classifier", ModelStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(modelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        modelRegistryService.promoteToActive(modelId);

        assertThat(candidate.getStatus()).isEqualTo(ModelStatus.ACTIVE);
        verify(modelRepository, times(1)).save(any());
    }

    @Test
    void promoteToActive_shouldPublishModelPromotedEvent() {
        AIModelRegistry candidate = buildModel("credit_classifier", 2, ModelStatus.EVALUATING);
        candidate.setId(modelId);

        when(modelRepository.findById(modelId)).thenReturn(Optional.of(candidate));
        when(modelRepository.findLatestActiveModel(any(), any())).thenReturn(Optional.empty());
        when(modelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        modelRegistryService.promoteToActive(modelId);

        ArgumentCaptor<ModelPromotedEvent> captor = ArgumentCaptor.forClass(ModelPromotedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ModelPromotedEvent event = captor.getValue();
        assertThat(event.getModelId()).isEqualTo(modelId);
        assertThat(event.getModelName()).isEqualTo("credit_classifier");
        assertThat(event.getModelVersion()).isEqualTo(2);
    }

    @Test
    void promoteToActive_shouldThrow_whenModelNotInEvaluatingStatus() {
        AIModelRegistry model = buildModel("credit_classifier", 1, ModelStatus.TRAINING);
        model.setId(modelId);

        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));

        assertThatThrownBy(() -> modelRegistryService.promoteToActive(modelId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EVALUATING");
    }

    @Test
    void promoteToActive_shouldThrow_whenModelNotFound() {
        when(modelRepository.findById(modelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modelRegistryService.promoteToActive(modelId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // retireModel
    // -------------------------------------------------------------------------

    @Test
    void retireModel_shouldSetRetiredStatusAndTimestamp() {
        AIModelRegistry model = buildModel("demand_forecaster", 1, ModelStatus.ACTIVE);
        model.setId(modelId);

        when(modelRepository.findById(modelId)).thenReturn(Optional.of(model));
        when(modelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AIModelRegistry result = modelRegistryService.retireModel(modelId);

        assertThat(result.getStatus()).isEqualTo(ModelStatus.RETIRED);
        assertThat(result.getRetiredAt()).isNotNull();
    }

    @Test
    void retireModel_shouldThrow_whenModelNotFound() {
        when(modelRepository.findById(modelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modelRegistryService.retireModel(modelId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Queries — delegate to repository
    // -------------------------------------------------------------------------

    @Test
    void getActiveModel_shouldDelegateToRepository() {
        AIModelRegistry model = buildModel("demand_forecaster", 1, ModelStatus.ACTIVE);
        when(modelRepository.findLatestActiveModel("demand_forecaster", ModelStatus.ACTIVE))
                .thenReturn(Optional.of(model));

        Optional<AIModelRegistry> result = modelRegistryService.getActiveModel("demand_forecaster");

        assertThat(result).isPresent();
        assertThat(result.get().getModelName()).isEqualTo("demand_forecaster");
    }

    @Test
    void getActiveModel_shouldReturnEmpty_whenNoActiveModel() {
        when(modelRepository.findLatestActiveModel("demand_forecaster", ModelStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(modelRegistryService.getActiveModel("demand_forecaster")).isEmpty();
    }

    @Test
    void getModel_shouldDelegateToRepository() {
        AIModelRegistry model = buildModel("demand_forecaster", 2, ModelStatus.RETIRED);
        when(modelRepository.findByModelNameAndModelVersion("demand_forecaster", 2))
                .thenReturn(Optional.of(model));

        Optional<AIModelRegistry> result = modelRegistryService.getModel("demand_forecaster", 2);

        assertThat(result).isPresent();
        assertThat(result.get().getModelVersion()).isEqualTo(2);
    }

    @Test
    void getAllVersions_shouldReturnAllVersionsOrderedByRepository() {
        List<AIModelRegistry> versions = List.of(
                buildModel("demand_forecaster", 3, ModelStatus.ACTIVE),
                buildModel("demand_forecaster", 2, ModelStatus.RETIRED),
                buildModel("demand_forecaster", 1, ModelStatus.RETIRED)
        );
        when(modelRepository.findAllVersionsByModelName("demand_forecaster")).thenReturn(versions);

        List<AIModelRegistry> result = modelRegistryService.getAllVersions("demand_forecaster");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getModelVersion()).isEqualTo(3);
    }

    @Test
    void getModelsByStatus_shouldDelegateToRepository() {
        List<AIModelRegistry> activeModels = List.of(
                buildModel("credit_classifier", 1, ModelStatus.ACTIVE),
                buildModel("demand_forecaster", 2, ModelStatus.ACTIVE)
        );
        when(modelRepository.findByStatus(ModelStatus.ACTIVE)).thenReturn(activeModels);

        List<AIModelRegistry> result = modelRegistryService.getModelsByStatus(ModelStatus.ACTIVE);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(m -> m.getStatus() == ModelStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AIModelRegistry buildModel(String name, int version, ModelStatus status) {
        return AIModelRegistry.builder()
                .id(UUID.randomUUID())
                .modelName(name)
                .modelVersion(version)
                .algorithm("xgboost")
                .status(status)
                .build();
    }
}
