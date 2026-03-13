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
     * DR Cash & Bank (paid amount) + DR AR (unpaid balance) / CR Sales Revenue
     * when a POS sale is completed.  If COGS + Inventory accounts are configured,
     * also posts DR COGS / CR Inventory for the sold items' cost.
     */
    void postPosSaleCompleted(PosSale sale);
}
