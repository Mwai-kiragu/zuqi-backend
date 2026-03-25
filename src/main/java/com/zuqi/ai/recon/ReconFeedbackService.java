package com.zuqi.ai.recon;

import com.zuqi.domain.ai.BankReconFeedback;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.user.User;
import com.zuqi.repository.BankReconFeedbackRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records human feedback on bank reconciliation match suggestions.
 *
 * Rejected matches become negative training examples for the HYBRID phase.
 * Accepted matches confirm auto-matches for ongoing model validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconFeedbackService {

    private final BankReconFeedbackRepository feedbackRepository;
    private final DistributorRepository distributorRepository;
    private final UserRepository userRepository;

    /**
     * Store user feedback on a suggested match.
     *
     * @param distributorId      distributor scope
     * @param matchId            UUID of the suggested match (ai_bank_recon_matches.id)
     * @param accepted           true if user accepted the match
     * @param correctedEntityId  if rejected, the UUID of the correct payment/entity
     * @param correctedEntityType if rejected, the type: PAYMENT or POS_SALE
     * @param amount             amount of the bank statement line (for pattern learning)
     * @param createdByUserId    user who submitted the feedback
     * @return the persisted feedback record
     */
    @Transactional
    public BankReconFeedback recordFeedback(UUID distributorId,
                                             UUID matchId,
                                             boolean accepted,
                                             UUID correctedEntityId,
                                             String correctedEntityType,
                                             Double amount,
                                             UUID createdByUserId) {
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + distributorId));

        User createdBy = userRepository.findById(createdByUserId).orElse(null);

        BankReconFeedback feedback = BankReconFeedback.builder()
                .distributor(distributor)
                .matchId(matchId)
                .accepted(accepted)
                .correctedEntityId(correctedEntityId)
                .correctedEntityType(correctedEntityType)
                .amount(amount)
                .createdBy(createdBy)
                .build();

        BankReconFeedback saved = feedbackRepository.save(feedback);

        log.info("Recon feedback recorded: matchId={} accepted={} distributorId={}",
                matchId, accepted, distributorId);
        return saved;
    }

    /**
     * Count feedback records to determine if enough data exists for HYBRID training.
     *
     * @param distributorId distributor scope
     * @return total feedback count
     */
    public long countFeedback(UUID distributorId) {
        return feedbackRepository.countByDistributorId(distributorId);
    }
}
