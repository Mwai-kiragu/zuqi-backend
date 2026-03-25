package com.zuqi.ai.procurement;

import com.zuqi.domain.ai.SupplierRiskScore;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.SupplierRepository;
import com.zuqi.repository.SupplierRiskScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierRiskScorerTest {

    @Mock SupplierFeatureServiceImpl featureService;
    @Mock SupplierRiskScoreRepository riskScoreRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock DistributorRepository distributorRepository;

    @InjectMocks
    SupplierRiskScorer scorer;

    private UUID supplierId;
    private UUID distributorId;
    private Supplier supplier;
    private Distributor distributor;

    @BeforeEach
    void setUp() {
        supplierId    = UUID.randomUUID();
        distributorId = UUID.randomUUID();

        supplier = new Supplier();
        supplier.setId(supplierId);

        distributor = new Distributor();
        distributor.setId(distributorId);

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(riskScoreRepository.findByDistributorIdAndSupplierId(distributorId, supplierId))
                .thenReturn(Optional.empty());
        when(riskScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void perfectSupplier_scoreNearHundred_tierPreferred() {
        when(featureService.computeFeatures(supplierId, distributorId)).thenReturn(
                new SupplierFeatures(supplierId, distributorId,
                        100.0, 0.0, 0.0, 0.0, 0.0, 20, 500_000, 24));

        SupplierRiskScore result = scorer.score(supplierId, distributorId);

        assertThat(result.getRiskScore()).isCloseTo(100.0, within(1.0));
        assertThat(result.getRiskTier()).isEqualTo("PREFERRED");
    }

    @Test
    void terribleSupplier_scoreBelow35_tierCritical() {
        when(featureService.computeFeatures(supplierId, distributorId)).thenReturn(
                new SupplierFeatures(supplierId, distributorId,
                        0.0, 30.0, 1.0, 5.0, 15.0, 2, 10_000, 0));

        SupplierRiskScore result = scorer.score(supplierId, distributorId);

        assertThat(result.getRiskScore()).isLessThan(35.0);
        assertThat(result.getRiskTier()).isEqualTo("CRITICAL");
    }

    @Test
    void midRangeSupplier_tierReliableOrAcceptable() {
        // onTime=70%, cv=0.2, responseTime=2d, tenure=8m → score in 50-80 range
        when(featureService.computeFeatures(supplierId, distributorId)).thenReturn(
                new SupplierFeatures(supplierId, distributorId,
                        70.0, 2.0, 0.0, 0.2, 2.0, 10, 200_000, 8));

        SupplierRiskScore result = scorer.score(supplierId, distributorId);

        assertThat(result.getRiskScore()).isBetween(50.0, 80.0);
        assertThat(result.getRiskTier()).isIn("RELIABLE", "ACCEPTABLE");
    }

    @Test
    void existingRecord_isUpdatedNotCreated() {
        SupplierRiskScore existing = SupplierRiskScore.builder()
                .distributor(distributor)
                .supplier(supplier)
                .build();
        when(riskScoreRepository.findByDistributorIdAndSupplierId(distributorId, supplierId))
                .thenReturn(Optional.of(existing));
        when(featureService.computeFeatures(supplierId, distributorId)).thenReturn(
                new SupplierFeatures(supplierId, distributorId,
                        85.0, 0.5, 0.0, 0.05, 0.5, 15, 300_000, 12));

        scorer.score(supplierId, distributorId);

        // Only one save call, no new entity built
        verify(riskScoreRepository, times(1)).save(existing);
    }

    @Test
    void subScores_areSetOnEntity() {
        when(featureService.computeFeatures(supplierId, distributorId)).thenReturn(
                new SupplierFeatures(supplierId, distributorId,
                        90.0, 1.0, 0.0, 0.1, 1.0, 12, 250_000, 10));

        ArgumentCaptor<SupplierRiskScore> captor = ArgumentCaptor.forClass(SupplierRiskScore.class);
        scorer.score(supplierId, distributorId);
        verify(riskScoreRepository).save(captor.capture());

        SupplierRiskScore saved = captor.getValue();
        assertThat(saved.getDeliveryReliabilityScore()).isCloseTo(90.0, within(0.1));
        assertThat(saved.getQualityScore()).isCloseTo(100.0, within(0.1));
        assertThat(saved.getPriceConsistencyScore()).isGreaterThanOrEqualTo(80.0);
        assertThat(saved.getResponsivenessScore()).isGreaterThan(85.0);
        assertThat(saved.getDataPhase()).isEqualTo("SYNTHETIC");
        assertThat(saved.getComputedAt()).isNotNull();
    }

    @Test
    void compositeIsClamped_between0And100() {
        // Force extreme values that might push formula beyond bounds
        when(featureService.computeFeatures(supplierId, distributorId)).thenReturn(
                new SupplierFeatures(supplierId, distributorId,
                        110.0, -5.0, -0.5, -1.0, -2.0, 50, 1_000_000, 100));

        SupplierRiskScore result = scorer.score(supplierId, distributorId);

        assertThat(result.getRiskScore()).isBetween(0.0, 100.0);
    }
}
