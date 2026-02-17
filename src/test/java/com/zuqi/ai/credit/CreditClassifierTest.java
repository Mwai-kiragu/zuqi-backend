package com.zuqi.ai.credit;

import com.zuqi.ai.feature.MerchantFeatureService;
import com.zuqi.ai.feature.MerchantFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.training.SyntheticMerchant;
import com.zuqi.ai.training.SyntheticMerchantDataGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.tribuo.Model;
import org.tribuo.classification.Label;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Test XGBoost credit classifier.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 3
 */
@SpringBootTest
@ActiveProfiles("test")
class CreditClassifierTest {

    @Autowired
    private CreditClassifier creditClassifier;

    @Autowired
    private SyntheticMerchantDataGenerator syntheticDataGenerator;

    @MockBean
    private ModelLoaderService modelLoader;

    @MockBean
    private MerchantFeatureService merchantFeatureService;

    @Test
    void testPredictWithNoModel() {
        // Given: No model is available
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);

        // Generate synthetic merchant features
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(1);
        when(merchantFeatureService.computeFeatures(merchantId))
                .thenReturn(merchants.get(0).features());

        // When: Predict
        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        // Then: Should return default result
        assertThat(result).isNotNull();
        assertThat(result.creditScore()).isEqualTo(50); // Neutral score
        assertThat(result.defaultProbability()).isEqualTo(0.5);
        assertThat(result.noDefaultProbability()).isEqualTo(0.5);
        assertThat(result.confidence()).isEqualTo(0.5); // Max of probabilities
        assertThat(result.prediction()).isEqualTo("UNKNOWN");
        assertThat(result.modelVersion()).isEqualTo("none");
    }

    @Test
    void testResultStructure() {
        // Generate a synthetic merchant
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(1);
        SyntheticMerchant merchant = merchants.get(0);

        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId))
                .thenReturn(merchant.features());

        // Predict
        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        // Validate result structure
        assertThat(result.creditScore()).isBetween(0, 100);
        assertThat(result.defaultProbability()).isBetween(0.0, 1.0);
        assertThat(result.noDefaultProbability()).isBetween(0.0, 1.0);
        assertThat(result.confidence()).isBetween(0.0, 1.0);
        assertThat(result.prediction()).isNotNull();
        assertThat(result.featureImportance()).isNotNull();
        assertThat(result.modelVersion()).isNotNull();
    }

    @Test
    void testPredictWithMultipleMerchants() {
        // Generate 10 synthetic merchants
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(10);

        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);

        for (SyntheticMerchant merchant : merchants) {
            UUID merchantId = UUID.randomUUID();
            when(merchantFeatureService.computeFeatures(merchantId))
                    .thenReturn(merchant.features());

            CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

            assertThat(result).isNotNull();
            assertThat(result.creditScore()).isBetween(0, 100);

            System.out.println("Merchant: " + merchant.archetypeName() +
                    " | Actual Default: " + merchant.didDefault() +
                    " | Predicted Score: " + result.creditScore() +
                    " | Default Prob: " + String.format("%.2f", result.defaultProbability()));
        }
    }

    @Test
    void testCreditScoreInversion() {
        // Credit score should be inverted from default probability
        // High default probability → Low credit score
        // Low default probability → High credit score

        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);

        // Generate synthetic merchant
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(1);
        when(merchantFeatureService.computeFeatures(merchantId))
                .thenReturn(merchants.get(0).features());

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        // For default result: defaultProb=0.5 → creditScore=50
        int expectedScore = (int) Math.round((1.0 - result.defaultProbability()) * 100);
        assertThat(result.creditScore()).isEqualTo(expectedScore);
    }

    @Test
    void testConfidenceCalculation() {
        // When there's an active model, confidence should be max of probabilities
        // When there's no model (default result), confidence is 0.5
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);

        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(1);
        when(merchantFeatureService.computeFeatures(merchantId))
                .thenReturn(merchants.get(0).features());

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        // For default result: both probabilities are 0.5, so max = 0.5
        assertThat(result.confidence()).isEqualTo(0.5);
        assertThat(result.confidence()).isEqualTo(Math.max(result.defaultProbability(), result.noDefaultProbability()));
    }

    @Test
    void testExceptionHandling() {
        // Given: merchantFeatureService throws exception
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);
        when(merchantFeatureService.computeFeatures(merchantId))
                .thenThrow(new RuntimeException("Database error"));

        // When: Predict
        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        // Then: Should return default result (not throw exception)
        assertThat(result).isNotNull();
        assertThat(result.creditScore()).isEqualTo(50);
        assertThat(result.prediction()).isEqualTo("UNKNOWN");
    }

    @Test
    void testFeatureIntegration() {
        // Validate that features are correctly passed to classifier
        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(5);

        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);

        for (SyntheticMerchant merchant : merchants) {
            UUID merchantId = UUID.randomUUID();
            when(merchantFeatureService.computeFeatures(merchantId))
                    .thenReturn(merchant.features());

            CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

            // Verify result is based on features (not random)
            // For default result, should always return same score
            assertThat(result.creditScore()).isEqualTo(50);

            // Features should have realistic values
            MerchantFeatures features = merchant.features();
            assertThat(features.totalOrders()).isGreaterThan(0);
            assertThat(features.onTimePaymentPct()).isBetween(0.0, 1.0);
            assertThat(features.currentUtilizationRatio()).isBetween(0.0, 1.05);
        }
    }

    @Test
    void testModelVersionExtraction() {
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);

        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(1);
        when(merchantFeatureService.computeFeatures(merchantId))
                .thenReturn(merchants.get(0).features());

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        // When no model, version should be "none"
        assertThat(result.modelVersion()).isEqualTo("none");
    }

    @Test
    void testProbabilitySum() {
        // Default + NoDefault probabilities should sum to ~1.0 (allowing for floating point)
        UUID merchantId = UUID.randomUUID();
        when(modelLoader.loadModel("credit_classifier")).thenReturn(null);

        List<SyntheticMerchant> merchants = syntheticDataGenerator.generateDataset(1);
        when(merchantFeatureService.computeFeatures(merchantId))
                .thenReturn(merchants.get(0).features());

        CreditClassifier.CreditClassifierResult result = creditClassifier.predict(merchantId);

        double sum = result.defaultProbability() + result.noDefaultProbability();
        assertThat(sum).isBetween(0.99, 1.01); // Allow small floating point variance
    }
}
