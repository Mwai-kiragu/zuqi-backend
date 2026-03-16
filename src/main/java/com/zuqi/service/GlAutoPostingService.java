package com.zuqi.service;

import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceItem;
import com.zuqi.domain.pos.PosSale;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface GlAutoPostingService {

    void postInvoiceCreated(Invoice invoice);

    void postPaymentReceived(Invoice invoice, BigDecimal amount);

    void postPosSaleCompleted(PosSale sale);

    void postPosCogs(PosSale sale);

    void postPosSalePayment(PosSale sale, BigDecimal paymentAmount);

    void postManualInvoiceCogs(Invoice invoice, List<InvoiceItem> items, UUID distributorId);
}
