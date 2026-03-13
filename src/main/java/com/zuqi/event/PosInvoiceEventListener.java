package com.zuqi.event;

import com.zuqi.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PosInvoiceEventListener {

    private final InvoiceService invoiceService;

    /**
     * Fires AFTER the POS sale transaction commits, in its own new transaction.
     * This ensures invoice creation is fully isolated from the sale transaction —
     * a failure here never rolls back the completed sale.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPosSaleCompleted(PosSaleCompletedEvent event) {
        try {
            invoiceService.createInvoiceFromPosSale(event.saleId());
            log.info("Invoice generated for POS sale: {}", event.saleId());
        } catch (Exception e) {
            log.warn("Invoice generation failed for POS sale {}: {}", event.saleId(), e.getMessage());
        }
    }
}
