package com.zuqi.service;

import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.pos.PosSale;

import java.math.BigDecimal;

/**
 * Automatically posts double-entry journal entries when financial transactions occur.
 * If the distributor has not configured the required system accounts, the posting
 * is silently skipped — the source transaction always succeeds regardless.
 */
public interface GlAutoPostingService {

    /** DR Accounts Receivable / CR Sales Revenue when an invoice is raised. */
    void postInvoiceCreated(Invoice invoice);

    /**
     * DR Cash & Bank / CR Accounts Receivable when a payment is recorded.
     * @param invoice  the invoice being paid
     * @param amount   the amount received in this payment
     */
    void postPaymentReceived(Invoice invoice, BigDecimal amount);

    /**
     * DR Accounts Receivable (full total) / CR Sales Revenue when a POS sale is completed.
     * Runs in its own REQUIRES_NEW transaction — isolated from COGS posting.
     */
    void postPosSaleCompleted(PosSale sale);

    /**
     * DR Cost of Goods Sold / CR Inventory for a POS sale.
     * Runs in a separate REQUIRES_NEW transaction so a missing COGS account never
     * rolls back the revenue entry posted by {@link #postPosSaleCompleted}.
     */
    void postPosCogs(PosSale sale);

    /**
     * DR Cash & Bank / CR Accounts Receivable when a payment is recorded against a POS sale.
     * @param sale          the POS sale being paid
     * @param paymentAmount the amount received in this single payment
     */
    void postPosSalePayment(PosSale sale, BigDecimal paymentAmount);
}
