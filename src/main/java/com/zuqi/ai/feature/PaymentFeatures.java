package com.zuqi.ai.feature;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-payment feature set for payment anomaly detection.
 * Used by IsolationForest to detect unusual payment patterns.
 *
 * Features capture:
 * - Payment timing relative to merchant norms
 * - Payment amount characteristics
 * - Payment method and time-of-day patterns
 * - Payment frequency gaps
 *
 * Blueprint reference: plan.md Section 4.2 - PaymentFeatureService
 */
@Builder
public record PaymentFeatures(
        UUID paymentId,
        UUID merchantId,
        LocalDateTime computedAt,

        // Payment timing features
        Double daysToPay,                              // Days from order date to payment date
        Double daysToPayVsMerchantAvg,                 // Deviation from merchant's average
        Integer gapSinceLastPaymentDays,               // Days since merchant's previous payment

        // Payment amount features
        BigDecimal paymentAmount,
        BigDecimal invoiceAmount,
        Double amountVsInvoiceRatio,                   // payment_amount / invoice_amount (1.0 = full, <1.0 = partial)
        Double amountVsMerchantAvg,                    // payment_amount / merchant_avg_payment

        // Payment characteristics
        String paymentMethodEncoded,                   // "MPESA", "CASH", "BANK", etc.
        Integer hourOfDay,                             // 0-23, hour when payment was made
        Boolean isPartial,                             // true if amount < invoice total
        Boolean isLate,                                // true if payment_date > due_date

        // Context features
        Integer merchantTotalPayments,                 // Total payment count for context
        BigDecimal merchantAvgPayment,                 // Merchant's average payment amount
        Double merchantAvgDaysToPay                    // Merchant's average days to pay
) {
}
