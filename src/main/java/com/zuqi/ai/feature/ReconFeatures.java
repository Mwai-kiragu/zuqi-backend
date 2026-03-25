package com.zuqi.ai.feature;

import java.util.UUID;

/**
 * Feature record for bank reconciliation matching.
 *
 * Computed per (bank statement line, candidate payment) pair.
 * No caching — evaluated at matching time.
 *
 * 8 features fed into the XGBoost classifier (MATCH / NO_MATCH).
 */
public record ReconFeatures(
        UUID statementLineId,
        UUID candidateEntityId,
        String candidateEntityType,   // PAYMENT, POS_SALE
        double amountDiffPct,         // |bank - candidate| / bank
        double amountExactMatch,      // 1.0 if diff < 1%, else 0.0
        int dateDiffDays,             // |bank_date - payment_date| in days
        double referenceExactMatch,   // 1.0 if reference strings match exactly
        double referenceSimilarity,   // 0–1 character overlap score
        double descriptionSimilarity, // 0–1 keyword overlap
        double sameMerchant,          // 1.0 if merchant id resolves to same entity
        double paymentMethodMatch     // 1.0 if payment method consistent with description
) {}
