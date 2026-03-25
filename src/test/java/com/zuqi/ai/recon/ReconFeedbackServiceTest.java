package com.zuqi.ai.recon;

import com.zuqi.domain.ai.BankReconFeedback;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.BankReconFeedbackRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconFeedbackServiceTest {

    @Mock private BankReconFeedbackRepository feedbackRepository;
    @Mock private DistributorRepository distributorRepository;
    @Mock private UserRepository userRepository;

    private ReconFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new ReconFeedbackService(feedbackRepository, distributorRepository, userRepository);
    }

    @Test
    void recordFeedback_accepted_savesFeedback() {
        UUID distributorId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Distributor distributor = mock(Distributor.class);
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        BankReconFeedback saved = mock(BankReconFeedback.class);
        when(feedbackRepository.save(any(BankReconFeedback.class))).thenReturn(saved);

        BankReconFeedback result = service.recordFeedback(
                distributorId, matchId, true, null, null, 15000.0, userId);

        assertThat(result).isSameAs(saved);
        verify(feedbackRepository).save(any(BankReconFeedback.class));
    }

    @Test
    void recordFeedback_rejected_savesFeedbackWithCorrection() {
        UUID distributorId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID correctedId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Distributor distributor = mock(Distributor.class);
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        BankReconFeedback saved = mock(BankReconFeedback.class);
        when(feedbackRepository.save(any(BankReconFeedback.class))).thenReturn(saved);

        BankReconFeedback result = service.recordFeedback(
                distributorId, matchId, false, correctedId, "PAYMENT", 25000.0, userId);

        assertThat(result).isNotNull();
        verify(feedbackRepository).save(any(BankReconFeedback.class));
    }

    @Test
    void recordFeedback_distributorNotFound_throwsException() {
        UUID distributorId = UUID.randomUUID();
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.recordFeedback(distributorId, UUID.randomUUID(), true,
                        null, null, 1000.0, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Distributor not found");
    }

    @Test
    void countFeedback_delegatesToRepository() {
        UUID distributorId = UUID.randomUUID();
        when(feedbackRepository.countByDistributorId(distributorId)).thenReturn(42L);

        long count = service.countFeedback(distributorId);

        assertThat(count).isEqualTo(42L);
    }

    @Test
    void countFeedback_whenNoFeedback_returnsZero() {
        UUID distributorId = UUID.randomUUID();
        when(feedbackRepository.countByDistributorId(distributorId)).thenReturn(0L);

        assertThat(service.countFeedback(distributorId)).isEqualTo(0L);
    }
}
