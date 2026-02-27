package com.zuqi.ai.synthetic.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * In-memory representation of a synthetic credit evaluation outcome.
 *
 * Used to build labelled training data for the credit risk classifier.
 * The {@code defaulted} flag provides the ground-truth label.
 *
 * @param syntheticId    UUID for cross-referencing
 * @param merchantRef    References {@link SyntheticMerchant#syntheticId()}
 * @param evaluationDate Date of the credit evaluation
 * @param grade          Credit grade: A, B, C, D, F
 * @param creditLimit    Approved credit limit at evaluation time
 * @param defaulted      TRUE if the merchant subsequently defaulted
 * @param daysToDefault  Days from evaluation to default event; null if defaulted=false
 */
public record SyntheticCreditEvaluation(
        UUID syntheticId,
        UUID merchantRef,
        LocalDate evaluationDate,
        String grade,
        BigDecimal creditLimit,
        boolean defaulted,
        Integer daysToDefault
) {}
