package com.zuqi.ai.pricing;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.ai.synthetic.SyntheticPricingFeatureBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tribuo.MutableDataset;
import org.tribuo.Trainer;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.RegressionFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingTrainingPipelineTest {

    @Mock SyntheticDataOrchestrator dataOrchestrator;
    @Mock SyntheticPricingFeatureBuilder syntheticFeatureBuilder;
    @Mock ModelEvaluator modelEvaluator;
    @Mock ModelRegistry modelRegistry;
    @Mock Trainer<Regressor> xgBoostRegressionTrainer;

    @InjectMocks
    PricingTrainingPipeline pipeline;

    @BeforeEach
    void setUp() {
        when(dataOrchestrator.generateBundle(any())).thenReturn(mock(SyntheticDataBundle.class));
    }

    private MutableDataset<Regressor> emptyDataset() {
        return new MutableDataset<>(
                new org.tribuo.provenance.SimpleDataSourceProvenance("test", new RegressionFactory()),
                new RegressionFactory());
    }

    @Test
    void insufficientExamples_returnsFailure() {
        when(syntheticFeatureBuilder.buildDataset(any(), any(UUID.class)))
                .thenReturn(emptyDataset()); // 0 examples

        PricingTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Insufficient");
        verifyNoInteractions(xgBoostRegressionTrainer);
    }

    @Test
    void trainerException_returnsFailure() {
        when(syntheticFeatureBuilder.buildDataset(any(), any(UUID.class)))
                .thenThrow(new RuntimeException("Dataset build failed"));

        PricingTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Dataset build failed");
    }

    @Test
    void rmseGateFailure_returnsFailure() throws Exception {
        MutableDataset<Regressor> dataset = buildMinimalDataset();
        when(syntheticFeatureBuilder.buildDataset(any(), any(UUID.class))).thenReturn(dataset);

        org.tribuo.Model<Regressor> mockModel = mock(org.tribuo.Model.class);
        when(xgBoostRegressionTrainer.train(any())).thenReturn(mockModel);

        ModelEvaluator.RegressorEvaluationResult badEval =
                ModelEvaluator.RegressorEvaluationResult.builder()
                        .rmse(200.0).mae(150.0).r2(0.1).build();
        when(modelEvaluator.evaluateRegressor(any(), any())).thenReturn(badEval);

        PricingTrainingPipeline.TrainingResult result = pipeline.runPipeline();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("RMSE gate");
    }

    @Test
    void modelNameConstant_isCorrect() {
        assertThat(PricingTrainingPipeline.MODEL_NAME).isEqualTo("smart_pricing_recommender");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private MutableDataset<Regressor> buildMinimalDataset() {
        // Build 15 examples so split produces non-empty train + test
        RegressionFactory factory = new RegressionFactory();
        MutableDataset<Regressor> dataset = new MutableDataset<>(
                new org.tribuo.provenance.SimpleDataSourceProvenance("test", factory), factory);

        for (int i = 0; i < 15; i++) {
            double price = 500.0 + i * 100;
            org.tribuo.regression.Regressor target = new org.tribuo.regression.Regressor("qty", 50.0 - i);
            dataset.add(new org.tribuo.impl.ArrayExample<>(target,
                    new String[]{"price", "cost_price", "margin_pct", "price_change_pct_30d",
                            "demand_at_current_price", "demand_trend", "inventory_days_of_supply",
                            "product_age_days", "similar_product_avg_price",
                            "price_vs_market_ratio", "product_category_encoded", "price_tier_encoded"},
                    new double[]{price, price * 0.65, 35.0, 0.0, 50.0 - i, 0.0, 30.0, 180.0,
                            price * 0.95, 1.05, 2.0, 1.0}));
        }
        return dataset;
    }
}
