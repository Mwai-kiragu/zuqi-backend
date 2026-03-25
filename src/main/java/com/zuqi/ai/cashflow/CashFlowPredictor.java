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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.regression.Regressor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates cash flow forecasts using the trained XGBoost regressor.
 *
 * Supports 7, 30, and 90-day horizons. For each day in the horizon:
 * - Compute features via CashFlowFeatureServiceImpl
 * - Predict net cash flow
 * - Apply residual percentiles for pessimistic/optimistic bounds
 * - Flag days where predicted net < 0 as shortfall warnings
 * - Persist to ai_cash_flow_forecasts (upsert by distributor + forecast_date)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashFlowPredictor {

    private final CashFlowFeatureServiceImpl featureService;
    private final CashFlowFeatureBuilder featureBuilder;
    private final ModelLoaderService modelLoader;
    private final ModelPhaseService phaseService;
    private final ModelRegistry modelRegistry;
    private final DataPhaseTracker phaseTracker;
    private final CashFlowForecastRepository forecastRepository;
    private final DistributorRepository distributorRepository;

    static final String MODEL_NAME = CashFlowTrainingPipeline.MODEL_NAME;

    /**
     * Generate forecasts for a distributor for the given number of days ahead.
     *
     * @param distributorId target distributor
     * @param horizonDays   7, 30, or 90
     * @return list of persisted CashFlowForecast records
     */
    public List<CashFlowForecast> forecast(UUID distributorId, int horizonDays) {
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        Model<Regressor> model = modelLoader.loadModel(MODEL_NAME);
        double[] residuals = loadResidualPercentiles();
        double confidence = phaseService.applyModifier(0.75, MODEL_NAME);
        String phase = phaseTracker.getPhase(MODEL_NAME, distributorId).name();

        List<CashFlowForecast> results = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int d = 1; d <= horizonDays; d++) {
            LocalDate forecastDate = today.plusDays(d);
            try {
                CashFlowFeatures features = featureService.computeFeatures(distributorId, forecastDate);
                double predictedNet = predictNet(model, features);
                double lower = residuals != null ? predictedNet + residuals[0] : predictedNet * 0.70;
                double upper = residuals != null ? predictedNet + residuals[1] : predictedNet * 1.30;

                // Simple inflow/outflow split: assume 60% of collections go in, rest out
                double predictedInflow = Math.max(0, features.avgDailyCollections7d() * 1.1);
                double predictedOutflow = Math.max(0, predictedInflow - predictedNet);

                boolean isShortfall = predictedNet < 0;
                if (isShortfall) {
                    log.warn("Cash flow shortfall predicted for distributor {} on date {}: net={}",
                            distributorId, forecastDate, String.format("%.0f", predictedNet));
                }

                CashFlowForecast forecast = CashFlowForecast.builder()
                        .distributor(distributor)
                        .forecastDate(forecastDate)
                        .predictedInflow(predictedInflow)
                        .predictedOutflow(predictedOutflow)
                        .predictedNet(predictedNet)
                        .lowerBoundNet(lower)
                        .upperBoundNet(upper)
                        .confidenceScore(confidence)
                        .dataPhase(phase)
                        .build();

                // Upsert: delete existing forecast for this date then save
                forecastRepository.deleteByDistributorIdAndForecastDate(distributorId, forecastDate);
                results.add(forecastRepository.save(forecast));

            } catch (Exception e) {
                log.warn("Failed to forecast day {} for distributor {}: {}",
                        forecastDate, distributorId, e.getMessage());
            }
        }

        log.info("Generated {} cash flow forecasts for distributor {} (horizon={}d)",
                results.size(), distributorId, horizonDays);
        return results;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private double predictNet(Model<Regressor> model, CashFlowFeatures features) {
        if (model == null) {
            // Fallback heuristic: recent net = collections minus expenses
            return features.avgDailyCollections7d() - features.avgDailyExpenses30d();
        }
        Example<Regressor> example = featureBuilder.buildExample(features);
        return model.predict(example).getOutput().getValues()[0];
    }

    private double[] loadResidualPercentiles() {
        return modelRegistry.getActiveModel(MODEL_NAME)
                .map(reg -> {
                    Map<String, Object> m = reg.getPerformanceMetrics();
                    if (m == null || !m.containsKey("lower_residual")) return null;
                    return new double[]{
                            ((Number) m.get("lower_residual")).doubleValue(),
                            ((Number) m.get("upper_residual")).doubleValue()
                    };
                })
                .orElse(null);
    }
}
