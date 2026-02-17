package com.zuqi.repository;

import com.zuqi.domain.credit.MerchantCreditOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for merchant credit outcomes.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md - Phase 3, Task 8
 */
@Repository
public interface MerchantCreditOutcomeRepository extends JpaRepository<MerchantCreditOutcome, UUID> {

    /**
     * Find all outcomes that haven't been used for training yet.
     * Used by retraining scheduler to fetch new data.
     */
    List<MerchantCreditOutcome> findByUsedForTrainingFalse();

    /**
     * Count outcomes by outcome type.
     */
    long countByOutcome(String outcome);

    /**
     * Count outcomes for a specific merchant and outcome type.
     */
    long countByMerchantIdAndOutcome(UUID merchantId, String outcome);

    /**
     * Count outcomes not yet used for training.
     */
    long countByUsedForTrainingFalse();

    /**
     * Get all outcomes for a merchant (chronological order).
     */
    List<MerchantCreditOutcome> findByMerchantIdOrderByOutcomeDateDesc(UUID merchantId);
}
