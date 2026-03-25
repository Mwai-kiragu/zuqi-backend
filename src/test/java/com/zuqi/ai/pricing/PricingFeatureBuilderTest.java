package com.zuqi.ai.pricing;

import com.zuqi.ai.demand.TribuoFeatureConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.regression.Regressor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PricingFeatureBuilder (no Spring context).
 */
class PricingFeatureBuilderTest {

    private PricingFeatureBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new PricingFeatureBuilder(new TribuoFeatureConverter());
    }

    private PricingFeatures features(double price, double cost, double demand) {
        return new PricingFeatures(
                UUID.randomUUID(), UUID.randomUUID(),
                price, cost,
                price > 0 ? (price - cost) / price * 100 : 0.0,
                0.0, demand, 5.0, 30.0, 180,
                price * 0.95, 2, 1
        );
    }

    @Test
    void buildTrainingExample_hasCorrectNumberOfFeatures() {
        PricingFeatures f = features(1000, 650, 50.0);
        Example<Regressor> example = builder.buildTrainingExample(f, 50.0, 1000.0);

        assertThat(example.size()).isEqualTo(12);
    }

    @Test
    void buildTrainingExample_labelIsTargetQty() {
        PricingFeatures f = features(500, 300, 30.0);
        Example<Regressor> example = builder.buildTrainingExample(f, 25.0, 500.0);

        assertThat(example.getOutput().getValues()[0]).isEqualTo(25.0);
    }

    @Test
    void buildInferenceExample_usesGivenCandidatePrice() {
        PricingFeatures f = features(1000, 600, 40.0);
        double candidatePrice = 1100.0;
        Example<Regressor> example = builder.buildInferenceExample(f, candidatePrice);

        // "price" feature should be candidatePrice
        double priceFeatureValue = -1.0;
        for (Feature feat : example) {
            if ("price".equals(feat.getName())) {
                priceFeatureValue = feat.getValue();
                break;
            }
        }
        assertThat(priceFeatureValue).isEqualTo(candidatePrice);
    }

    @Test
    void buildDataset_containsAllExamples() {
        PricingFeatures f1 = features(500,  300, 20.0);
        PricingFeatures f2 = features(1000, 650, 15.0);
        PricingFeatures f3 = features(2500, 1500, 8.0);

        MutableDataset<Regressor> dataset = builder.buildDataset(
                List.of(f1, f2, f3),
                List.of(20.0, 15.0, 8.0),
                List.of(500.0, 1000.0, 2500.0));

        assertThat(dataset.size()).isEqualTo(3);
    }

    @Test
    void priceVsMarketRatio_computedCorrectly() {
        // similarProductAvgPrice = 950; candidatePrice = 1000 → ratio = 1000/950 ≈ 1.053
        PricingFeatures f = new PricingFeatures(
                UUID.randomUUID(), UUID.randomUUID(),
                1000.0, 650.0, 35.0, 0.0, 50.0, 5.0, 30.0, 180, 950.0, 2, 1);

        Example<Regressor> example = builder.buildInferenceExample(f, 1000.0);

        double ratio = -1.0;
        for (Feature feat : example) {
            if ("price_vs_market_ratio".equals(feat.getName())) {
                ratio = feat.getValue();
                break;
            }
        }
        assertThat(ratio).isCloseTo(1000.0 / 950.0, org.assertj.core.api.Assertions.within(0.001));
    }

    @Test
    void encodePriceTier_returnsCorrectBucket() {
        PricingFeatureServiceImpl svc = new PricingFeatureServiceImpl(null, null);

        assertThat(svc.encodePriceTier(200)).isEqualTo(0);
        assertThat(svc.encodePriceTier(500)).isEqualTo(1);
        assertThat(svc.encodePriceTier(1999)).isEqualTo(1);
        assertThat(svc.encodePriceTier(2001)).isEqualTo(2);
    }
}
