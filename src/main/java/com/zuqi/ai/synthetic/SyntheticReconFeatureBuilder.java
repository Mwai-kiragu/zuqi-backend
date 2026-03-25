package com.zuqi.ai.synthetic;

import com.zuqi.ai.feature.ReconFeatures;
import com.zuqi.ai.synthetic.dto.SyntheticBankStatementLine;
import com.zuqi.ai.synthetic.dto.SyntheticPayment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Builds labelled ReconFeatures pairs from synthetic bank statement lines and
 * synthetic payments for training the bank reconciliation classifier.
 *
 * True pairs (MATCH) come from matching lines paired with their true payment.
 * False pairs (NO_MATCH) come in two flavours:
 *  1. Easy negatives: random payment (very different amount/merchant)
 *  2. Hard negatives: payment with a similar amount but different merchant —
 *     these force the model to learn beyond amount matching alone.
 */
@Component
@Slf4j
public class SyntheticReconFeatureBuilder {

    /**
     * Build labelled pairs for training.
     * Produces ~50% MATCH, ~25% easy NO_MATCH, ~25% hard NO_MATCH.
     */
    public List<LabelledReconExample> buildLabelledExamples(
            List<SyntheticBankStatementLine> lines,
            List<SyntheticPayment> payments) {

        List<LabelledReconExample> examples = new ArrayList<>();
        if (payments.isEmpty()) return examples;

        // Sort payments by amount for efficient similar-amount lookup
        List<SyntheticPayment> sortedByAmount = payments.stream()
                .sorted(Comparator.comparingDouble(p -> p.amount().doubleValue()))
                .toList();

        Random rng = new Random(42L);

        for (SyntheticBankStatementLine line : lines) {
            if (line.isMatchingLine() && line.trueMatchPaymentId() != null) {
                SyntheticPayment truePayment = payments.stream()
                        .filter(p -> p.syntheticId().equals(line.trueMatchPaymentId()))
                        .findFirst()
                        .orElse(null);
                if (truePayment != null) {
                    examples.add(new LabelledReconExample(computeFeatures(line, truePayment), "MATCH"));
                }
            } else if (!line.isMatchingLine()) {
                if (rng.nextBoolean()) {
                    // Easy negative: random payment
                    SyntheticPayment randomPayment = payments.get(rng.nextInt(payments.size()));
                    examples.add(new LabelledReconExample(computeFeatures(line, randomPayment), "NO_MATCH"));
                } else {
                    // Hard negative: payment with similar amount but different merchant
                    SyntheticPayment hardNeg = findSimilarAmountDifferentMerchant(
                            line, sortedByAmount, rng);
                    examples.add(new LabelledReconExample(computeFeatures(line, hardNeg), "NO_MATCH"));
                }
            }
        }

        long matchCount = examples.stream().filter(e -> "MATCH".equals(e.label())).count();
        log.info("Built {} recon labelled examples ({} MATCH, {} NO_MATCH)",
                examples.size(), matchCount, examples.size() - matchCount);
        return examples;
    }

    /**
     * Find a payment whose amount is within 5% of the line amount but belongs to a
     * different merchant. Falls back to the closest-amount payment if no such payment exists.
     */
    private SyntheticPayment findSimilarAmountDifferentMerchant(
            SyntheticBankStatementLine line,
            List<SyntheticPayment> sortedByAmount,
            Random rng) {

        double lineAmount = line.amount().doubleValue();
        double tolerance  = lineAmount * 0.05; // within 5%

        List<SyntheticPayment> candidates = sortedByAmount.stream()
                .filter(p -> Math.abs(p.amount().doubleValue() - lineAmount) <= tolerance)
                .filter(p -> line.merchantRef() == null
                        || !line.merchantRef().equals(p.merchantRef()))
                .toList();

        if (!candidates.isEmpty()) {
            return candidates.get(rng.nextInt(candidates.size()));
        }

        // Fallback: find the payment with the closest amount overall (different merchant preferred)
        return sortedByAmount.stream()
                .filter(p -> line.merchantRef() == null
                        || !line.merchantRef().equals(p.merchantRef()))
                .min(Comparator.comparingDouble(
                        p -> Math.abs(p.amount().doubleValue() - lineAmount)))
                .orElse(sortedByAmount.get(0));
    }

    /**
     * Compute ReconFeatures for a (line, payment) pair.
     * Mirrors ReconFeatureServiceImpl logic for synthetic data.
     */
    public ReconFeatures computeFeatures(SyntheticBankStatementLine line,
                                          SyntheticPayment payment) {
        double bankAmount = line.amount().doubleValue();
        double payAmount = payment.amount().doubleValue();

        // Amount diff as percentage of bank amount
        double amountDiffPct = bankAmount > 0
                ? Math.abs(bankAmount - payAmount) / bankAmount
                : 1.0;
        double amountExactMatch = amountDiffPct < 0.01 ? 1.0 : 0.0;

        // Date difference in days
        int dateDiffDays = (int) Math.abs(
                line.statementDate().toEpochDay()
                - payment.paymentDate().toLocalDate().toEpochDay());

        // Reference similarity
        String lineRef = line.reference() != null ? line.reference().toUpperCase() : "";
        String payRef = payment.syntheticId().toString().substring(0, 8).toUpperCase();
        double referenceExactMatch = lineRef.equals(payRef) ? 1.0 : 0.0;
        double referenceSimilarity = lineRef.isEmpty() ? 0.0 : prefixSimilarity(lineRef, payRef);

        // Description similarity: does description contain payment method?
        String desc = line.description() != null ? line.description().toUpperCase() : "";
        String method = payment.paymentMethod() != null ? payment.paymentMethod().toUpperCase() : "";
        double descriptionSimilarity = (method.length() > 0 && desc.contains(method)) ? 0.8 : 0.1;

        // Same merchant
        double sameMerchant = (line.merchantRef() != null
                && line.merchantRef().equals(payment.merchantRef())) ? 1.0 : 0.0;

        // Payment method match: MPESA in description and MPESA payment
        boolean methodInDesc = desc.contains("MPESA") || desc.contains("BANK_TRANSFER")
                || desc.contains("TRANSFER");
        boolean paymentIsMpesa = "MPESA".equalsIgnoreCase(method);
        double paymentMethodMatch = (methodInDesc && (
                (desc.contains("MPESA") && paymentIsMpesa)
                || (!desc.contains("MPESA") && !paymentIsMpesa))) ? 1.0 : 0.0;

        return new ReconFeatures(
                line.syntheticId(),
                payment.syntheticId(),
                "PAYMENT",
                amountDiffPct,
                amountExactMatch,
                dateDiffDays,
                referenceExactMatch,
                referenceSimilarity,
                descriptionSimilarity,
                sameMerchant,
                paymentMethodMatch
        );
    }

    /** Simple prefix overlap ratio as a string similarity measure. */
    private double prefixSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int minLen = Math.min(a.length(), b.length());
        int matching = 0;
        for (int i = 0; i < minLen; i++) {
            if (a.charAt(i) == b.charAt(i)) matching++;
            else break;
        }
        return (double) matching / Math.max(a.length(), b.length());
    }

    public record LabelledReconExample(ReconFeatures features, String label) {}
}
