package com.zuqi.ai.pricing;

import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.synthetic.DataPhaseTracker;
import com.zuqi.domain.ai.PricingRecommendation;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.product.Product;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.PricingRecommendationRepository;
import com.zuqi.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.regression.Regressor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmartPricingRecommenderTest {

    @Mock PricingFeatureServiceImpl featureService;
    @Mock PricingFeatureBuilder featureBuilder;
    @Mock ModelLoaderService modelLoader;
    @Mock ModelPhaseService phaseService;
    @Mock ModelRegistry modelRegistry;
    @Mock DataPhaseTracker phaseTracker;
    @Mock PricingRecommendationRepository recommendationRepository;
    @Mock ProductRepository productRepository;
    @Mock DistributorRepository distributorRepository;

    @InjectMocks SmartPricingRecommender recommender;

    private UUID productId;
    private UUID distributorId;
    private Product product;
    private Distributor distributor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        productId     = UUID.randomUUID();
        distributorId = UUID.randomUUID();

        product = new Product();
        product.setId(productId);
        product.setUnitPrice(BigDecimal.valueOf(1000));

        distributor = new Distributor();
        distributor.setId(distributorId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(phaseTracker.getPhase(anyString(), any())).thenReturn(com.zuqi.domain.ai.DataPhase.SYNTHETIC);
        when(phaseService.applyModifier(anyDouble(), anyString())).thenAnswer(i -> i.getArgument(0));
        when(modelRegistry.getActiveModel(anyString())).thenReturn(Optional.empty());
        when(recommendationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Default: feature builder returns a dummy example
        Example<Regressor> dummyExample = mock(Example.class);
        when(featureBuilder.buildInferenceExample(any(), anyDouble())).thenReturn(dummyExample);
    }

    @Test
    void modelNotAvailable_returnsNull() throws Exception {
        when(featureService.computeFeatures(productId, distributorId)).thenReturn(
                features(1000, 650));
        when(modelLoader.loadModel(anyString())).thenThrow(new RuntimeException("Model not found"));

        PricingRecommendation result = recommender.recommend(productId, distributorId);

        assertThat(result).isNull();
        verifyNoInteractions(recommendationRepository);
    }

    @Test
    void priceInsensitiveProduct_recommendsPriceIncrease() throws Exception {
        PricingFeatures f = features(1000, 650);
        when(featureService.computeFeatures(productId, distributorId)).thenReturn(f);

        Model<Regressor> model = mockModelWithDemandFunction(1000, 50);
        when(modelLoader.loadModel(anyString())).thenReturn(model);

        PricingRecommendation rec = recommender.recommend(productId, distributorId);

        assertThat(rec).isNotNull();
        // With price-insensitive demand, higher price → higher revenue
        assertThat(rec.getRecommendedPrice()).isGreaterThanOrEqualTo(1000.0);
    }

    @Test
    void zeroPriceProduct_returnsNull() throws Exception {
        when(featureService.computeFeatures(productId, distributorId)).thenReturn(
                features(0, 0));

        PricingRecommendation result = recommender.recommend(productId, distributorId);

        assertThat(result).isNull();
    }

    @Test
    void savedRecommendation_hasCorrectFields() throws Exception {
        PricingFeatures f = features(1000, 650);
        when(featureService.computeFeatures(productId, distributorId)).thenReturn(f);

        Model<Regressor> model = mockModelWithDemandFunction(1000, 50);
        when(modelLoader.loadModel(anyString())).thenReturn(model);

        PricingRecommendation rec = recommender.recommend(productId, distributorId);

        assertThat(rec).isNotNull();
        assertThat(rec.getCurrentPrice()).isEqualTo(1000.0);
        assertThat(rec.getRecommendedPrice()).isGreaterThan(0.0);
        assertThat(rec.getStatus()).isEqualTo("PENDING");
        assertThat(rec.getDataPhase()).isEqualTo("SYNTHETIC");
        assertThat(rec.getReason()).isNotBlank();
    }

    @Test
    void priceSensitiveProduct_doesNotRecommendLargeIncrease() throws Exception {
        PricingFeatures f = features(1000, 650);
        when(featureService.computeFeatures(productId, distributorId)).thenReturn(f);

        // Demand drops sharply with price: demand = 200 - 0.1 * price
        Model<Regressor> model = mockModelWithPriceSensitiveDemand();
        when(modelLoader.loadModel(anyString())).thenReturn(model);

        PricingRecommendation rec = recommender.recommend(productId, distributorId);

        assertThat(rec).isNotNull();
        // For price-sensitive products, +15% price would reduce demand a lot
        // Optimal should be at or below current price
        assertThat(rec.getRecommendedPrice()).isLessThanOrEqualTo(1100.0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PricingFeatures features(double price, double cost) {
        return new PricingFeatures(
                productId, distributorId,
                price, cost,
                price > 0 ? (price - cost) / price * 100 : 0.0,
                0.0, 50.0, 2.0, 30.0, 180, price * 0.95, 2, 1);
    }

    // Functional interface to avoid Tribuo predict() overload ambiguity
    @FunctionalInterface
    interface SinglePredictor {
        Prediction<Regressor> predict(Example<Regressor> ex);
    }

    @SuppressWarnings("unchecked")
    private Model<Regressor> stubPredict(SinglePredictor fn) {
        Model<Regressor> model = mock(Model.class);
        // Use a spy-style delegation via Answer — bypass overload ambiguity
        // by matching on the non-generic Example type and calling our fn
        lenient().doAnswer(inv -> fn.predict((Example<Regressor>) inv.getArgument(0)))
                .when(model).predict((Example<Regressor>) argThat(arg -> arg instanceof Example));
        return model;
    }

    /**
     * Returns constant demand (50 units) at any price — price-insensitive.
     */
    private Model<Regressor> mockModelWithDemandFunction(double basePrice, double baseDemand) {
        return stubPredict(ex -> {
            Regressor output = new Regressor("qty", baseDemand);
            return new Prediction<>(output, 0, ex);
        });
    }

    /**
     * Returns demand that drops fast with higher price — price-sensitive.
     */
    private Model<Regressor> mockModelWithPriceSensitiveDemand() {
        // calls 0..5 correspond to factors 0.90, 0.95, 1.00, 1.05, 1.10, 1.15
        double[] demands = {55.0, 52.0, 50.0, 45.0, 38.0, 28.0};
        int[] counter = {0};
        return stubPredict(ex -> {
            int idx = Math.min(counter[0]++, demands.length - 1);
            Regressor output = new Regressor("qty", demands[idx]);
            return new Prediction<>(output, 0, ex);
        });
    }
}
