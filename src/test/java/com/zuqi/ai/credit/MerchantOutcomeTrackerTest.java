package com.zuqi.ai.credit;

import com.zuqi.domain.credit.MerchantCreditOutcome;
import com.zuqi.repository.MerchantCreditOutcomeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MerchantOutcomeTracker.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md - Phase 3, Task 8
 */
@ExtendWith(MockitoExtension.class)
class MerchantOutcomeTrackerTest {

    @Mock
    private MerchantCreditOutcomeRepository outcomeRepository;

    @InjectMocks
    private MerchantOutcomeTracker tracker;

    @Test
    void testRecordDefault() {
        // Given: Merchant who defaulted
        UUID merchantId = UUID.randomUUID();
        UUID creditApplicationId = UUID.randomUUID();
        UUID recordedBy = UUID.randomUUID();
        String reason = "30+ days overdue";

        // When: Record default
        tracker.recordDefault(merchantId, creditApplicationId, reason, recordedBy);

        // Then: Outcome should be saved
        ArgumentCaptor<MerchantCreditOutcome> captor =
                ArgumentCaptor.forClass(MerchantCreditOutcome.class);

        verify(outcomeRepository).save(captor.capture());

        MerchantCreditOutcome saved = captor.getValue();
        assertThat(saved.getMerchantId()).isEqualTo(merchantId);
        assertThat(saved.getCreditApplicationId()).isEqualTo(creditApplicationId);
        assertThat(saved.getOutcome()).isEqualTo("DEFAULT");
        assertThat(saved.getReason()).isEqualTo(reason);
        assertThat(saved.getRecordedBy()).isEqualTo(recordedBy);
        assertThat(saved.getUsedForTraining()).isFalse();
    }

    @Test
    void testRecordSuccess() {
        // Given: Merchant who succeeded
        UUID merchantId = UUID.randomUUID();
        UUID creditApplicationId = UUID.randomUUID();
        String reason = "All payments on time";

        // When: Record success
        tracker.recordSuccess(merchantId, creditApplicationId, reason);

        // Then: Outcome should be saved
        ArgumentCaptor<MerchantCreditOutcome> captor =
                ArgumentCaptor.forClass(MerchantCreditOutcome.class);

        verify(outcomeRepository).save(captor.capture());

        MerchantCreditOutcome saved = captor.getValue();
        assertThat(saved.getMerchantId()).isEqualTo(merchantId);
        assertThat(saved.getOutcome()).isEqualTo("NO_DEFAULT");
        assertThat(saved.getReason()).isEqualTo(reason);
        assertThat(saved.getRecordedBy()).isNull(); // System-generated
        assertThat(saved.getUsedForTraining()).isFalse();
    }

    @Test
    void testGetUnusedOutcomesForTraining() {
        // Given: Unused outcomes exist
        List<MerchantCreditOutcome> mockOutcomes = List.of(
                createMockOutcome("DEFAULT"),
                createMockOutcome("NO_DEFAULT"),
                createMockOutcome("DEFAULT")
        );

        when(outcomeRepository.findByUsedForTrainingFalse()).thenReturn(mockOutcomes);

        // When: Fetch unused outcomes
        List<MerchantCreditOutcome> outcomes = tracker.getUnusedOutcomesForTraining();

        // Then: Should return unused outcomes
        assertThat(outcomes).hasSize(3);
        verify(outcomeRepository).findByUsedForTrainingFalse();
    }

    @Test
    void testMarkOutcomesAsUsed() {
        // Given: Outcome IDs to mark as used
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<UUID> outcomeIds = List.of(id1, id2);

        MerchantCreditOutcome outcome1 = createMockOutcome("DEFAULT");
        MerchantCreditOutcome outcome2 = createMockOutcome("NO_DEFAULT");

        when(outcomeRepository.findAllById(outcomeIds))
                .thenReturn(List.of(outcome1, outcome2));

        // When: Mark as used
        tracker.markOutcomesAsUsed(outcomeIds);

        // Then: Outcomes should be marked and saved
        assertThat(outcome1.getUsedForTraining()).isTrue();
        assertThat(outcome2.getUsedForTraining()).isTrue();
        verify(outcomeRepository).saveAll(any());
    }

    @Test
    void testGetStatistics() {
        // Given: Outcome statistics
        when(outcomeRepository.count()).thenReturn(100L);
        when(outcomeRepository.countByOutcome("DEFAULT")).thenReturn(25L);
        when(outcomeRepository.countByOutcome("NO_DEFAULT")).thenReturn(75L);
        when(outcomeRepository.countByUsedForTrainingFalse()).thenReturn(15L);

        // When: Get statistics
        MerchantOutcomeTracker.OutcomeStatistics stats = tracker.getStatistics();

        // Then: Statistics should be correct
        assertThat(stats.totalOutcomes()).isEqualTo(100L);
        assertThat(stats.defaultCount()).isEqualTo(25L);
        assertThat(stats.successCount()).isEqualTo(75L);
        assertThat(stats.unusedForTraining()).isEqualTo(15L);
        assertThat(stats.defaultRate()).isEqualTo(0.25);
    }

    @Test
    void testHasDefaultHistory() {
        // Given: Merchant with default history
        UUID merchantId = UUID.randomUUID();
        when(outcomeRepository.countByMerchantIdAndOutcome(merchantId, "DEFAULT"))
                .thenReturn(2L);

        // When: Check for default history
        boolean hasDefaults = tracker.hasDefaultHistory(merchantId);

        // Then: Should return true
        assertThat(hasDefaults).isTrue();
    }

    @Test
    void testHasNoDefaultHistory() {
        // Given: Merchant with no defaults
        UUID merchantId = UUID.randomUUID();
        when(outcomeRepository.countByMerchantIdAndOutcome(merchantId, "DEFAULT"))
                .thenReturn(0L);

        // When: Check for default history
        boolean hasDefaults = tracker.hasDefaultHistory(merchantId);

        // Then: Should return false
        assertThat(hasDefaults).isFalse();
    }

    /**
     * Helper: Create mock outcome.
     */
    private MerchantCreditOutcome createMockOutcome(String outcome) {
        return MerchantCreditOutcome.builder()
                .id(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .outcome(outcome)
                .usedForTraining(false)
                .build();
    }
}
