package com.zuqi.ai.recon;

import com.zuqi.ai.feature.ReconFeatures;
import com.zuqi.ai.model.ModelLoaderService;
import com.zuqi.ai.model.ModelPhaseService;
import com.zuqi.domain.payment.Payment;
import com.zuqi.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tribuo.Example;
import org.tribuo.Model;
import org.tribuo.Prediction;
import org.tribuo.classification.Label;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Matches bank statement lines to payments using the trained XGBoost classifier.
 *
 * Thresholds (from phase2-plan.md Section 3.1):
 * - >= 0.95 probability → AUTO-MATCH (no human review needed)
 * - 0.50–0.95           → SUGGEST (present to user for confirmation)
 * - < 0.50              → NO_MATCH (skip)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankReconMatcher {

    private static final double AUTO_MATCH_THRESHOLD = 0.95;
    private static final double SUGGEST_THRESHOLD = 0.50;
    private static final int DATE_WINDOW_DAYS = 7;
    private static final double AMOUNT_TOLERANCE_PCT = 0.10;

    private final ReconFeatureBuilder featureBuilder;
    private final ModelLoaderService modelLoader;
    private final ModelPhaseService phaseService;
    private final PaymentRepository paymentRepository;

    static final String MODEL_NAME = ReconTrainingPipeline.MODEL_NAME;

    /**
     * Find matching payments for a bank statement line.
     *
     * @param distributorId  distributor scope
     * @param bankAmount     amount on the bank statement line
     * @param statementDate  date the transaction appeared on the statement
     * @param description    narrative text from the bank statement
     * @param reference      reference field from the bank statement (may be null)
     * @return list of candidates ranked by match probability, descending
     */
    public List<MatchResult> findMatches(UUID distributorId,
                                          BigDecimal bankAmount,
                                          LocalDate statementDate,
                                          String description,
                                          String reference) {
        LocalDateTime from = statementDate.minusDays(DATE_WINDOW_DAYS).atStartOfDay();
        LocalDateTime to = statementDate.plusDays(DATE_WINDOW_DAYS).atTime(23, 59, 59);

        List<Payment> candidates = paymentRepository
                .findByDistributorIdAndPaymentDateBetween(distributorId, from, to);

        if (candidates.isEmpty()) {
            log.debug("No candidate payments for distributor {} date {} amount {}",
                    distributorId, statementDate, bankAmount);
            return List.of();
        }

        // Filter by amount window
        double lowerBound = bankAmount.doubleValue() * (1.0 - AMOUNT_TOLERANCE_PCT);
        double upperBound = bankAmount.doubleValue() * (1.0 + AMOUNT_TOLERANCE_PCT);
        candidates = candidates.stream()
                .filter(p -> p.getAmount() != null)
                .filter(p -> {
                    double amt = p.getAmount().doubleValue();
                    return amt >= lowerBound && amt <= upperBound;
                })
                .toList();

        Model<Label> model;
        try {
            model = modelLoader.loadModel(MODEL_NAME);
        } catch (Error e) {
            log.error("[BankRecon] Fatal error loading model (native library issue?): {}", e.getMessage(), e);
            model = null;
        }
        List<MatchResult> results = new ArrayList<>();

        for (Payment candidate : candidates) {
            ReconFeatures features = buildFeatures(bankAmount, statementDate, description,
                    reference, candidate);
            double matchProbability = scoreCandidate(model, features);
            double confidence = phaseService.applyModifier(matchProbability, MODEL_NAME);

            if (confidence < SUGGEST_THRESHOLD) continue;

            String disposition = confidence >= AUTO_MATCH_THRESHOLD ? "AUTO_MATCH" : "SUGGEST";
            results.add(new MatchResult(candidate.getId(), candidate,
                    matchProbability, confidence, disposition));
        }

        results.sort(Comparator.comparingDouble(MatchResult::matchProbability).reversed());
        log.info("Recon: {} candidates, {} passed threshold for date={} amount={}",
                candidates.size(), results.size(), statementDate, bankAmount);
        return results;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private ReconFeatures buildFeatures(BigDecimal bankAmount, LocalDate statementDate,
                                         String description, String reference,
                                         Payment candidate) {
        double payAmount = candidate.getAmount().doubleValue();
        double bankAmt = bankAmount.doubleValue();

        double amountDiffPct = bankAmt > 0 ? Math.abs(bankAmt - payAmount) / bankAmt : 1.0;
        double amountExactMatch = amountDiffPct < 0.01 ? 1.0 : 0.0;

        LocalDate payDate = candidate.getPaymentDate() != null
                ? candidate.getPaymentDate().toLocalDate() : statementDate;
        int dateDiffDays = (int) Math.abs(statementDate.toEpochDay() - payDate.toEpochDay());

        String desc = description != null ? description.toUpperCase() : "";

        // Payment method: Payment has paymentMethod relation; check its name
        String methodName = (candidate.getPaymentMethod() != null
                && candidate.getPaymentMethod().getName() != null)
                ? candidate.getPaymentMethod().getName().toUpperCase() : "";
        double paymentMethodMatch = (!methodName.isEmpty() && desc.contains(methodName)) ? 1.0 : 0.0;

        // Merchant: the merchant field on Payment is a Customer entity
        double sameMerchant = (candidate.getMerchant() != null
                && candidate.getMerchant().getBusinessName() != null
                && desc.contains(candidate.getMerchant().getBusinessName().toUpperCase()))
                ? 1.0 : 0.0;

        return new ReconFeatures(
                null,
                candidate.getId(),
                "PAYMENT",
                amountDiffPct,
                amountExactMatch,
                dateDiffDays,
                0.0,
                0.0,
                0.2,
                sameMerchant,
                paymentMethodMatch
        );
    }

    private double scoreCandidate(Model<Label> model, ReconFeatures features) {
        if (model == null) {
            // Heuristic fallback when no model is trained yet
            return features.amountExactMatch() * 0.5
                    + (features.dateDiffDays() <= 3 ? 0.3 : 0.0)
                    + features.sameMerchant() * 0.2;
        }

        Example<Label> example = featureBuilder.buildExample(features);
        Prediction<Label> prediction = model.predict(example);

        // Extract probability for MATCH label from output scores
        return prediction.getOutputScores().entrySet().stream()
                .filter(e -> ReconFeatureBuilder.LABEL_MATCH.equals(e.getKey()))
                .mapToDouble(e -> e.getValue().getScore())
                .findFirst()
                .orElse(ReconFeatureBuilder.LABEL_MATCH.equals(
                        prediction.getOutput().getLabel()) ? 0.8 : 0.2);
    }

    public record MatchResult(
            UUID paymentId,
            Payment payment,
            double matchProbability,
            double confidenceScore,
            String disposition    // AUTO_MATCH or SUGGEST
    ) {}
}
