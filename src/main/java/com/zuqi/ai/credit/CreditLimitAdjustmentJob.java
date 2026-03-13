package com.zuqi.ai.credit;

import com.zuqi.domain.credit.CreditLimit;
import com.zuqi.domain.credit.CreditLimitStatus;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.domain.customer.Customer;
import com.zuqi.repository.CreditLimitRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.CustomerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Monthly batch job that adjusts credit limits for all active merchants
 * using the ML-powered {@link CreditLimitRegressor}.
 *
 * <p>Business rules applied on top of the raw ML prediction:
 * <ul>
 *   <li>Floor: {@code minLimitKes} — no limit can fall below this value.</li>
 *   <li>Cap on increase: the model may recommend an unlimited increase, but the
 *       system enforces a maximum of {@code maxIncreasePct}% per cycle.</li>
 *   <li>Decreases require human review — they are stored but flagged
 *       {@code requiresReview = true} so credit officers can approve them.</li>
 * </ul>
 *
 * <p>Blueprint reference: implementation_plan.md Phase 6 — Credit Limit Adjustment
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditLimitAdjustmentJob {

    private final CreditLimitRegressor   creditLimitRegressor;
    private final CustomerRepository     customerRepository;
    private final CreditLimitRepository  creditLimitRepository;
    private final DistributorRepository  distributorRepository;
    private final MeterRegistry          meterRegistry;

    @Value("${zuqi.ai.credit.adjustment-enabled:false}")
    private boolean adjustmentEnabled;

    /** Maximum permitted single-cycle increase expressed as a percentage (default 20%). */
    @Value("${zuqi.ai.credit.max-increase-pct:20.0}")
    private double maxIncreasePct;

    /** Absolute floor for any credit limit in KES (default 50,000). */
    @Value("${zuqi.ai.credit.min-limit-kes:50000.0}")
    private double minLimitKes;

    // ── Scheduled entry point ─────────────────────────────────────────────

    /**
     * Monthly credit-limit adjustment job.
     * Default cron runs at 01:00 on the 1st of every month.
     */
    @Scheduled(cron = "${zuqi.ai.credit.adjustment-cron:0 0 1 1 * *}")
    public void runMonthlyAdjustment() {
        if (!adjustmentEnabled) {
            log.debug("Credit limit adjustment job is disabled (zuqi.ai.credit.adjustment-enabled=false)");
            return;
        }
        log.info("=".repeat(80));
        log.info("MONTHLY CREDIT LIMIT ADJUSTMENT JOB - STARTED");
        log.info("=".repeat(80));

        long startTime = System.currentTimeMillis();
        int totalSuccess = 0;
        int totalFailure = 0;

        List<Distributor> distributors = distributorRepository.findByActiveTrue();
        log.info("Processing {} active distributors", distributors.size());

        for (Distributor distributor : distributors) {
            List<Customer> merchants =
                    customerRepository.findByDistributorIdAndActiveTrue(distributor.getId());

            log.info("Distributor [{}] '{}': adjusting {} merchant credit limits",
                    distributor.getId(), distributor.getName(), merchants.size());

            for (Customer merchant : merchants) {
                try {
                    adjustCreditLimit(merchant, distributor.getId());
                    totalSuccess++;
                    meterRegistry.counter("zuqi_ai_credit_limit_adjustments",
                            "result", "success").increment();
                } catch (Exception e) {
                    totalFailure++;
                    meterRegistry.counter("zuqi_ai_credit_limit_adjustments",
                            "result", "failure").increment();
                    log.warn("Credit limit adjustment failed for merchant [{}]: {}",
                            merchant.getId(), e.getMessage());
                }
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("=".repeat(80));
        log.info("MONTHLY CREDIT LIMIT ADJUSTMENT COMPLETE - {}ms | success={} failure={}",
                durationMs, totalSuccess, totalFailure);
        log.info("=".repeat(80));
    }

    // ── Public single-merchant entry point (REST API) ─────────────────────

    /**
     * Adjust the credit limit for a single merchant.
     * This is the public entry point used by the REST controller.
     *
     * @param merchantId Merchant to evaluate
     * @return {@link CreditAdjustmentResult} describing the outcome
     * @throws IllegalArgumentException if the merchant does not exist
     */
    @Transactional
    public CreditAdjustmentResult adjustCreditLimit(UUID merchantId) {
        Customer merchant = customerRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Merchant not found: " + merchantId));

        UUID distributorId = merchant.getDistributor() != null
                ? merchant.getDistributor().getId()
                : null;

        return adjustCreditLimit(merchant, distributorId);
    }

    // ── Private batch helper ──────────────────────────────────────────────

    /**
     * Core credit-limit adjustment logic.
     * Called by both the scheduled batch and the public REST entry point.
     *
     * @param merchant      Merchant entity (already loaded)
     * @param distributorId Distributor scope for the credit limit lookup
     * @return {@link CreditAdjustmentResult}
     */
    @Transactional
    CreditAdjustmentResult adjustCreditLimit(Customer merchant, UUID distributorId) {
        UUID merchantId = merchant.getId();

        // 1. Predict new limit via ML regressor (returns BigDecimal in KES)
        BigDecimal predicted = creditLimitRegressor.predictCreditLimit(merchantId);
        double predictedLimit = predicted.doubleValue();

        // 2. Apply minimum floor
        double flooredLimit = Math.max(predictedLimit, minLimitKes);

        // 3. Load existing active credit limit (if any)
        Optional<CreditLimit> existingOpt = distributorId != null
                ? creditLimitRepository.findByMerchantIdAndDistributorIdAndStatus(
                        merchantId, distributorId, CreditLimitStatus.ACTIVE)
                : Optional.empty();

        double previousLimitKes = 0.0;
        double finalLimit;
        String action;
        boolean requiresReview = false;

        if (existingOpt.isPresent()) {
            CreditLimit existing = existingOpt.get();
            previousLimitKes = existing.getApprovedLimit().doubleValue();

            // 4a. Cap upward movement to maxIncreasePct per cycle
            double maxAllowed = previousLimitKes * (1.0 + maxIncreasePct / 100.0);

            if (flooredLimit > previousLimitKes) {
                // Increase — cap at maxAllowed
                finalLimit = Math.min(flooredLimit, maxAllowed);
                action = "INCREASED";
            } else if (flooredLimit < previousLimitKes) {
                // Decrease — use as-is but flag for human review
                finalLimit = flooredLimit;
                action = "DECREASED";
                requiresReview = true;
            } else {
                finalLimit = previousLimitKes;
                action = "UNCHANGED";
            }

            // 5a. Update existing entity
            existing.setApprovedLimit(toBigDecimal(finalLimit));
            existing.setAvailableLimit(computeAvailableLimit(existing, toBigDecimal(finalLimit)));
            creditLimitRepository.save(existing);

            log.debug("Updated credit limit for merchant [{}]: {} -> {} KES ({})",
                    merchantId, previousLimitKes, finalLimit, action);

        } else {
            // 4b. No existing limit — create a new one
            finalLimit = flooredLimit;
            action = "CREATED";

            CreditLimit newLimit = buildNewCreditLimit(merchant, distributorId, toBigDecimal(finalLimit));
            creditLimitRepository.save(newLimit);

            log.debug("Created credit limit for merchant [{}]: {} KES", merchantId, finalLimit);
        }

        // 6. Calculate percentage change
        double changePct = previousLimitKes > 0.0
                ? ((finalLimit - previousLimitKes) / previousLimitKes) * 100.0
                : 0.0;

        return new CreditAdjustmentResult(
                merchantId,
                previousLimitKes,
                finalLimit,
                changePct,
                action,
                requiresReview
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Recompute available limit after an adjustment, preserving the currently
     * utilised balance so we don't inadvertently free up capacity that doesn't exist.
     */
    private BigDecimal computeAvailableLimit(CreditLimit existing, BigDecimal newApprovedLimit) {
        BigDecimal utilized = existing.getUtilizedAmount() != null
                ? existing.getUtilizedAmount()
                : BigDecimal.ZERO;
        BigDecimal available = newApprovedLimit.subtract(utilized);
        return available.max(BigDecimal.ZERO);
    }

    private CreditLimit buildNewCreditLimit(Customer merchant, UUID distributorId,
                                            BigDecimal approvedLimit) {
        return CreditLimit.builder()
                .merchant(merchant)
                .distributor(merchant.getDistributor())
                .approvedLimit(approvedLimit)
                .availableLimit(approvedLimit)
                .utilizedAmount(BigDecimal.ZERO)
                .status(CreditLimitStatus.ACTIVE)
                .build();
    }

    // ── Result record ─────────────────────────────────────────────────────

    /**
     * Result of a credit limit adjustment operation.
     *
     * @param merchantId       Merchant that was evaluated
     * @param previousLimitKes Previous approved limit in KES (0 if newly created)
     * @param newLimitKes      New approved limit in KES after business rules
     * @param changePct        Percentage change from previous to new (0 if newly created)
     * @param action           One of: INCREASED, DECREASED, UNCHANGED, CREATED
     * @param requiresReview   {@code true} when the ML model recommended a decrease
     *                         and a credit officer must approve before it takes effect
     */
    public record CreditAdjustmentResult(
            UUID    merchantId,
            double  previousLimitKes,
            double  newLimitKes,
            double  changePct,
            String  action,
            boolean requiresReview
    ) {}
}
