package com.zuqi.ai.synthetic;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * In-memory representation of a synthetic payment.
 *
 * Mirrors {@link com.zuqi.domain.payment.Payment} fields relevant for
 * credit scoring and payment anomaly detection feature computation.
 *
 * @param syntheticId      UUID for cross-referencing
 * @param invoiceRef       References {@link SyntheticOrder#syntheticId()} (the invoice being paid)
 * @param merchantRef      References {@link SyntheticMerchant#syntheticId()}
 * @param amount           Amount paid (may be partial)
 * @param paymentDate      Timestamp of payment
 * @param paymentMethod    Method: CASH, MPESA, KCB_TRANSFER, CREDIT
 * @param daysAfterInvoice Days elapsed between order date and payment date
 * @param isPartial        TRUE if amount < full invoice amount
 * @param isDefault        TRUE if this payment was never made (simulated missed payment)
 */
public record SyntheticPayment(
        UUID syntheticId,
        UUID invoiceRef,
        UUID merchantRef,
        BigDecimal amount,
        LocalDateTime paymentDate,
        String paymentMethod,
        int daysAfterInvoice,
        boolean isPartial,
        boolean isDefault
) {}
