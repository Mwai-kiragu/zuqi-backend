package com.zuqi.ai.cashflow;

import com.zuqi.ai.feature.CashFlowFeatureServiceImpl;
import com.zuqi.ai.feature.CashFlowFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.CashFlowForecast;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.CashFlowForecastRepository;
import com.zuqi.repository.DistributorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashFlowPredictorTest {

    @Mock private CashFlowFeatureServiceImpl featureService;
    @Mock private CashFlowFeatureBuilder featureBuilder;
    @Mock private ModelLoaderService modelLoader;
    @Mock private ModelPhaseService phaseService;
    @Mock private ModelRegistry modelRegistry;
    @Mock private DataPhaseTracker phaseTracker;
    @Mock private CashFlowForecastRepository forecastRepository;
    @Mock private DistributorRepository distributorRepository;

    private CashFlowPredictor predictor;

    @BeforeEach
    void setUp() {
        predictor = new CashFlowPredictor(
                featureService, featureBuilder, modelLoader, phaseService,
                modelRegistry, phaseTracker, forecastRepository, distributorRepository);
    }

    private CashFlowFeatures stubFeatures(UUID distributorId) {
        return new CashFlowFeatures(
                distributorId, LocalDate.now(),
                100_000.0, 20_000.0, 18_000.0, 2_000.0,
                50_000.0, 30_000.0, 80_000.0, 12_000.0,
                40_000.0, 3, 15, 0.0, 0.0, 3_000.0, 1_000.0
        );
    }

    @Test
    void forecast_distributorNotFound_throwsException() {
        UUID distributorId = UUID.randomUUID();
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> predictor.forecast(distributorId, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Distributor not found");
    }

    @Test
    void forecast_7dayHorizon_returnsSeven_forecasts() {
        UUID distributorId = UUID.randomUUID();
        Distributor distributor = mock(Distributor.class);
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(modelLoader.loadModel(anyString())).thenReturn(null);
        when(modelRegistry.getActiveModel(anyString())).thenReturn(Optional.empty());
        when(phaseService.applyModifier(anyDouble(), anyString())).thenReturn(0.75);
        when(phaseTracker.getPhase(anyString(), eq(distributorId)))
                .thenReturn(com.zuqi.domain.ai.DataPhase.SYNTHETIC);
        when(featureService.computeFeatures(eq(distributorId), any(LocalDate.class)))
                .thenAnswer(inv -> stubFeatures(distributorId));

        CashFlowForecast saved = mock(CashFlowForecast.class);
        when(forecastRepository.save(any(CashFlowForecast.class))).thenReturn(saved);

        List<CashFlowForecast> results = predictor.forecast(distributorId, 7);

        assertThat(results).hasSize(7);
        verify(forecastRepository, times(7)).save(any(CashFlowForecast.class));
        verify(forecastRepository, times(7))
                .deleteByDistributorIdAndForecastDate(eq(distributorId), any(LocalDate.class));
    }

    @Test
    void forecast_30dayHorizon_returns30Forecasts() {
        UUID distributorId = UUID.randomUUID();
        Distributor distributor = mock(Distributor.class);
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(modelLoader.loadModel(anyString())).thenReturn(null);
        when(modelRegistry.getActiveModel(anyString())).thenReturn(Optional.empty());
        when(phaseService.applyModifier(anyDouble(), anyString())).thenReturn(0.70);
        when(phaseTracker.getPhase(anyString(), eq(distributorId)))
                .thenReturn(com.zuqi.domain.ai.DataPhase.SYNTHETIC);
        when(featureService.computeFeatures(eq(distributorId), any(LocalDate.class)))
                .thenAnswer(inv -> stubFeatures(distributorId));

        CashFlowForecast saved = mock(CashFlowForecast.class);
        when(forecastRepository.save(any(CashFlowForecast.class))).thenReturn(saved);

        List<CashFlowForecast> results = predictor.forecast(distributorId, 30);

        assertThat(results).hasSize(30);
    }

    @Test
    void forecast_noModel_fallbackHeuristicReturnsResults() {
        UUID distributorId = UUID.randomUUID();
        Distributor distributor = mock(Distributor.class);
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(modelLoader.loadModel(anyString())).thenReturn(null);    // no model
        when(modelRegistry.getActiveModel(anyString())).thenReturn(Optional.empty());
        when(phaseService.applyModifier(anyDouble(), anyString())).thenReturn(0.60);
        when(phaseTracker.getPhase(anyString(), eq(distributorId)))
                .thenReturn(com.zuqi.domain.ai.DataPhase.SYNTHETIC);
        when(featureService.computeFeatures(eq(distributorId), any(LocalDate.class)))
                .thenAnswer(inv -> stubFeatures(distributorId));
        when(forecastRepository.save(any())).thenReturn(mock(CashFlowForecast.class));

        List<CashFlowForecast> results = predictor.forecast(distributorId, 3);

        assertThat(results).hasSize(3);
    }

    @Test
    void forecast_whenFeatureServiceThrows_skipsDay_logsWarning() {
        UUID distributorId = UUID.randomUUID();
        Distributor distributor = mock(Distributor.class);
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(modelLoader.loadModel(anyString())).thenReturn(null);
        when(modelRegistry.getActiveModel(anyString())).thenReturn(Optional.empty());
        when(phaseService.applyModifier(anyDouble(), anyString())).thenReturn(0.70);
        when(phaseTracker.getPhase(anyString(), eq(distributorId)))
                .thenReturn(com.zuqi.domain.ai.DataPhase.SYNTHETIC);
        when(featureService.computeFeatures(eq(distributorId), any(LocalDate.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        List<CashFlowForecast> results = predictor.forecast(distributorId, 5);

        // All days should be skipped, no exception propagated
        assertThat(results).isEmpty();
        verify(forecastRepository, never()).save(any());
    }

    @Test
    void modelName_matchesTrainingPipeline() {
        assertThat(CashFlowPredictor.MODEL_NAME).isEqualTo(CashFlowTrainingPipeline.MODEL_NAME);
    }
}
