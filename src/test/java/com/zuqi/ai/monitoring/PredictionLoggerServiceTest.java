package com.zuqi.ai.monitoring;

import com.zuqi.domain.ai.AIPrediction;
import com.zuqi.domain.ai.EntityType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.AIPredictionRepository;
import com.zuqi.repository.DistributorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictionLoggerServiceTest {

    @Mock
    private AIPredictionRepository predictionRepository;

    @Mock
    private DistributorRepository distributorRepository;

    @InjectMocks
    private PredictionLoggerService predictionLoggerService;

    private UUID distributorId;
    private UUID merchantId;
    private UUID predictionId;
    private Distributor distributor;

    @BeforeEach
    void setUp() {
        distributorId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        predictionId = UUID.randomUUID();
        distributor = Distributor.builder().id(distributorId).build();
    }

    // -------------------------------------------------------------------------
    // logPrediction
    // -------------------------------------------------------------------------

    @Test
    void logPrediction_shouldSaveWithAllCorrectFields() {
        Map<String, Object> prediction = Map.of("grade", "B", "limit", 85000);
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(predictionRepository.save(any(AIPrediction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AIPrediction result = predictionLoggerService.logPrediction(
                "credit_classifier", 2,
                EntityType.MERCHANT, merchantId,
                distributorId, prediction, 0.88,
                "abc123hash");

        assertThat(result.getModelName()).isEqualTo("credit_classifier");
        assertThat(result.getModelVersion()).isEqualTo(2);
        assertThat(result.getEntityType()).isEqualTo(EntityType.MERCHANT);
        assertThat(result.getEntityId()).isEqualTo(merchantId);
        assertThat(result.getDistributor()).isEqualTo(distributor);
        assertThat(result.getPredictionValue()).isEqualTo(prediction);
        assertThat(result.getConfidenceScore()).isEqualTo(0.88);
        assertThat(result.getInputFeaturesHash()).isEqualTo("abc123hash");
        verify(predictionRepository).save(any(AIPrediction.class));
    }

    @Test
    void logPrediction_shouldSaveWithNullConfidence_whenNotProvided() {
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(predictionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AIPrediction result = predictionLoggerService.logPrediction(
                "demand_forecaster", 1,
                EntityType.MERCHANT, merchantId,
                distributorId, Map.of("qty", 24), null, null);

        assertThat(result.getConfidenceScore()).isNull();
        assertThat(result.getInputFeaturesHash()).isNull();
    }

    @Test
    void logPrediction_shouldThrow_whenDistributorNotFound() {
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> predictionLoggerService.logPrediction(
                "credit_classifier", 1,
                EntityType.MERCHANT, merchantId,
                distributorId, Map.of(), 0.5, "hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(distributorId.toString());

        verifyNoInteractions(predictionRepository);
    }

    // -------------------------------------------------------------------------
    // logOverride
    // -------------------------------------------------------------------------

    @Test
    void logOverride_shouldSetAllOverrideFields() {
        AIPrediction existing = buildPrediction("credit_classifier", 1, false);
        Map<String, Object> overrideVal = Map.of("grade", "A", "limit", 120000);

        when(predictionRepository.findById(predictionId)).thenReturn(Optional.of(existing));
        when(predictionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AIPrediction result = predictionLoggerService.logOverride(
                predictionId, overrideVal, "finance_user", "Manual review — strong payment history");

        assertThat(result.getWasOverridden()).isTrue();
        assertThat(result.getOverrideValue()).isEqualTo(overrideVal);
        assertThat(result.getOverrideBy()).isEqualTo("finance_user");
        assertThat(result.getOverrideReason()).isEqualTo("Manual review — strong payment history");
        verify(predictionRepository).save(existing);
    }

    @Test
    void logOverride_shouldThrow_whenPredictionNotFound() {
        when(predictionRepository.findById(predictionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> predictionLoggerService.logOverride(
                predictionId, Map.of(), "user", "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(predictionId.toString());
    }

    // -------------------------------------------------------------------------
    // getPredictionHistory
    // -------------------------------------------------------------------------

    @Test
    void getPredictionHistory_shouldReturnPagedResults() {
        List<AIPrediction> predictions = List.of(
                buildPrediction("credit_classifier", 1, false),
                buildPrediction("credit_classifier", 1, true)
        );
        when(predictionRepository.findByEntity(
                eq(EntityType.MERCHANT), eq(merchantId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(predictions));

        List<AIPrediction> result = predictionLoggerService.getPredictionHistory(
                EntityType.MERCHANT, merchantId, 10);

        assertThat(result).hasSize(2);
        verify(predictionRepository).findByEntity(
                eq(EntityType.MERCHANT), eq(merchantId), eq(PageRequest.of(0, 10)));
    }

    @Test
    void getPredictionHistory_shouldReturnEmpty_whenNoPredictions() {
        when(predictionRepository.findByEntity(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        List<AIPrediction> result = predictionLoggerService.getPredictionHistory(
                EntityType.MERCHANT, merchantId, 5);

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // getLatestPrediction
    // -------------------------------------------------------------------------

    @Test
    void getLatestPrediction_shouldDelegateToRepository() {
        AIPrediction latest = buildPrediction("credit_classifier", 2, false);
        when(predictionRepository.findLatestByEntity(EntityType.MERCHANT, merchantId))
                .thenReturn(Optional.of(latest));

        Optional<AIPrediction> result = predictionLoggerService.getLatestPrediction(
                EntityType.MERCHANT, merchantId);

        assertThat(result).isPresent();
        assertThat(result.get().getModelVersion()).isEqualTo(2);
    }

    @Test
    void getLatestPrediction_shouldReturnEmpty_whenNoPredictionsExist() {
        when(predictionRepository.findLatestByEntity(EntityType.MERCHANT, merchantId))
                .thenReturn(Optional.empty());

        assertThat(predictionLoggerService.getLatestPrediction(EntityType.MERCHANT, merchantId))
                .isEmpty();
    }

    // -------------------------------------------------------------------------
    // calculateOverrideRate
    // -------------------------------------------------------------------------

    @Test
    void calculateOverrideRate_shouldReturnZero_whenNoPredictionsExist() {
        when(predictionRepository.findByModelNameAndVersion("credit_classifier", 1))
                .thenReturn(List.of());

        double rate = predictionLoggerService.calculateOverrideRate("credit_classifier", 1);

        assertThat(rate).isZero();
    }

    @Test
    void calculateOverrideRate_shouldReturnZero_whenNoOverrides() {
        List<AIPrediction> predictions = List.of(
                buildPrediction("credit_classifier", 1, false),
                buildPrediction("credit_classifier", 1, false),
                buildPrediction("credit_classifier", 1, false)
        );
        when(predictionRepository.findByModelNameAndVersion("credit_classifier", 1))
                .thenReturn(predictions);

        double rate = predictionLoggerService.calculateOverrideRate("credit_classifier", 1);

        assertThat(rate).isZero();
    }

    @Test
    void calculateOverrideRate_shouldCalculateCorrectly() {
        List<AIPrediction> predictions = List.of(
                buildPrediction("credit_classifier", 1, true),
                buildPrediction("credit_classifier", 1, true),
                buildPrediction("credit_classifier", 1, false),
                buildPrediction("credit_classifier", 1, false)
        );
        when(predictionRepository.findByModelNameAndVersion("credit_classifier", 1))
                .thenReturn(predictions);

        double rate = predictionLoggerService.calculateOverrideRate("credit_classifier", 1);

        assertThat(rate).isEqualTo(0.5); // 2 overrides out of 4
    }

    @Test
    void calculateOverrideRate_shouldReturnOne_whenAllPredictionsOverridden() {
        List<AIPrediction> predictions = List.of(
                buildPrediction("credit_classifier", 1, true),
                buildPrediction("credit_classifier", 1, true)
        );
        when(predictionRepository.findByModelNameAndVersion("credit_classifier", 1))
                .thenReturn(predictions);

        double rate = predictionLoggerService.calculateOverrideRate("credit_classifier", 1);

        assertThat(rate).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AIPrediction buildPrediction(String modelName, int version, boolean overridden) {
        return AIPrediction.builder()
                .id(UUID.randomUUID())
                .modelName(modelName)
                .modelVersion(version)
                .entityType(EntityType.MERCHANT)
                .entityId(merchantId)
                .distributor(distributor)
                .predictionValue(Map.of("grade", "B"))
                .confidenceScore(0.75)
                .wasOverridden(overridden)
                .build();
    }
}
