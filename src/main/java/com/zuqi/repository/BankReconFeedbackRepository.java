package com.zuqi.repository;

import com.zuqi.domain.ai.BankReconFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for bank reconciliation feedback records.
 * Rejected matches are surfaced for model retraining.
 */
@Repository
public interface BankReconFeedbackRepository extends JpaRepository<BankReconFeedback, UUID> {

    /**
     * Find all records where the AI match was rejected by the user.
     * These corrections are used as negative training examples.
     */
    List<BankReconFeedback> findByDistributorIdAndAcceptedFalse(UUID distributorId);

    /**
     * Count all feedback records for a distributor.
     */
    long countByDistributorId(UUID distributorId);
}
