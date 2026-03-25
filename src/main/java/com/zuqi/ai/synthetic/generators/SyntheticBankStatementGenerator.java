package com.zuqi.ai.synthetic.generators;

import com.zuqi.ai.synthetic.dto.SyntheticBankStatementLine;
import com.zuqi.ai.synthetic.dto.SyntheticPayment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Generates synthetic bank statement lines for training the bank reconciliation
 * classifier (Model #11).
 *
 * Distribution (from phase2-plan.md Section 3.1):
 * - 70% of lines are MATCHING lines derived from synthetic payments with noise:
 *     - Date shifted ±0–3 days
 *     - Description = trading name (not system name)
 *     - Amount: 90% exact, 10% ±small fee difference (KES 5–50)
 *     - Reference: 80% present, 20% missing/truncated
 * - 30% are random NON-MATCHING lines (deposits, transfers, other income)
 */
@Component
@Slf4j
public class SyntheticBankStatementGenerator {

    private static final String[] TRADING_NAME_PREFIXES = {
            "MWANGI STORES", "KAMAU SHOP", "WANJIKU ENTERPRISES", "OTIENO GENERAL",
            "KIPLANGAT TRADERS", "NJOROGE SUPERMARKET", "ACHIENG WHOLESALE",
            "KIMANI DISTRIBUTORS", "OUMA HARDWARE", "MBUGUA KIOSK"
    };

    private static final String[] RANDOM_DESCRIPTIONS = {
            "CASH DEPOSIT", "BANK TRANSFER IN", "MOBILE MONEY DEPOSIT",
            "MPESA PAYBILL", "EQUITY BANK TRANSFER", "KCB MPESA",
            "SALARY CREDIT", "LOAN DISBURSEMENT", "REVERSAL CREDIT"
    };

    /**
     * Generate bank statement lines from a list of synthetic payments.
     *
     * @param payments source payments (from PaymentBehaviorGenerator)
     * @param seed     random seed for reproducibility
     * @return list of synthetic bank statement lines
     */
    public List<SyntheticBankStatementLine> generate(
            List<SyntheticPayment> payments, long seed) {

        Random rng = new Random(seed);
        List<SyntheticBankStatementLine> lines = new ArrayList<>();

        // ── 70%: matching lines derived from real payments ──────────────────
        int matchingCount = (int) (payments.size() * 0.70);
        List<SyntheticPayment> sourcePayments = payments.size() <= matchingCount
                ? payments
                : payments.subList(0, matchingCount);

        for (SyntheticPayment payment : sourcePayments) {
            // Date noise: ±0–3 days
            int dateDrift = rng.nextInt(4);  // 0, 1, 2, or 3
            LocalDate statementDate = payment.paymentDate().toLocalDate().plusDays(dateDrift);

            // Amount noise: 90% exact, 10% ±small fee
            BigDecimal amount;
            if (rng.nextDouble() < 0.90) {
                amount = payment.amount();
            } else {
                double fee = 5.0 + rng.nextInt(46); // KES 5–50
                amount = payment.amount().subtract(BigDecimal.valueOf(fee)).max(BigDecimal.ONE);
            }

            // Reference: 80% present, 20% missing
            String reference;
            if (rng.nextDouble() < 0.80) {
                String baseRef = payment.syntheticId().toString().substring(0, 8).toUpperCase();
                // 50% chance of truncation
                reference = rng.nextBoolean() ? baseRef : baseRef.substring(0, 4);
            } else {
                reference = null;
            }

            // Description: use trading name, not system name
            String tradingName = TRADING_NAME_PREFIXES[rng.nextInt(TRADING_NAME_PREFIXES.length)];
            String method = payment.paymentMethod().equalsIgnoreCase("MPESA")
                    ? "MPESA" : "BANK_TRANSFER";
            String description = method + " " + tradingName;

            lines.add(new SyntheticBankStatementLine(
                    UUID.randomUUID(),
                    payment.syntheticId(),
                    payment.merchantRef(),
                    amount,
                    statementDate,
                    description,
                    reference,
                    method,
                    true,
                    payment.syntheticId()
            ));
        }

        // ── 30%: random non-matching lines ─────────────────────────────────
        int nonMatchingCount = (int) (payments.size() * 0.30);
        for (int i = 0; i < nonMatchingCount; i++) {
            double randomAmount = 1_000 + rng.nextInt(499_000); // KES 1k–500k
            LocalDate randomDate = LocalDate.now().minusDays(rng.nextInt(180));
            String desc = RANDOM_DESCRIPTIONS[rng.nextInt(RANDOM_DESCRIPTIONS.length)];

            lines.add(new SyntheticBankStatementLine(
                    UUID.randomUUID(),
                    null,
                    null,
                    BigDecimal.valueOf(randomAmount).setScale(2, RoundingMode.HALF_UP),
                    randomDate,
                    desc,
                    null,
                    rng.nextBoolean() ? "MPESA" : "BANK_TRANSFER",
                    false,
                    null
            ));
        }

        log.info("Generated {} synthetic bank statement lines ({} matching, {} non-matching)",
                lines.size(), matchingCount, nonMatchingCount);
        return lines;
    }
}
