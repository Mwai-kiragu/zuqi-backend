package com.zuqi.service;

import com.zuqi.domain.ft.FundsTransfer;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceItem;
import com.zuqi.domain.pos.PosSale;
import com.zuqi.domain.supplier.SupplierBill;

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

    /** DR Inventory (GOODS) or DR Other Expense (SERVICES), CR Accounts Payable */
    void postSupplierBillReceived(SupplierBill bill);

    /** DR Accounts Payable, CR Cash & Bank */
    void postSupplierPaymentDisbursed(FundsTransfer ft, BigDecimal amount);

    /** DR Expense (general), CR Cash & Bank — for EXTERNAL / non-supplier disbursements */
    void postExternalTransferDisbursed(FundsTransfer ft, BigDecimal amount);
}
