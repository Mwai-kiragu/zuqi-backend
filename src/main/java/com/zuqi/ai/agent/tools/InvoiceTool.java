package com.zuqi.ai.agent.tools;

import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.repository.InvoiceRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceTool {

    private final InvoiceRepository invoiceRepository;

    @Tool("Get invoice summary for a distributor. Returns counts by status (DRAFT, UNPAID, SENT, PAID, " +
         "PARTIALLY_PAID, OVERDUE, CANCELLED), total invoiced amount (KES), and outstanding balance due. " +
         "Use for questions about invoices, billing, outstanding amounts, overdue invoices.")
    @Transactional(readOnly = true)
    public String getInvoiceSummary(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getInvoiceSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            long total           = invoiceRepository.countByDistributorId(distId);
            long draft           = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.DRAFT);
            long unpaid          = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.UNPAID);
            long sent            = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.SENT);
            long paid            = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.PAID);
            long partiallyPaid   = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.PARTIALLY_PAID);
            long overdue         = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.OVERDUE);
            long cancelled       = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.CANCELLED);

            return String.format(
                "{ \"tool\": \"InvoiceSummary\", \"distributorId\": \"%s\", " +
                "\"totalInvoices\": %d, \"draft\": %d, \"unpaid\": %d, \"sent\": %d, " +
                "\"paid\": %d, \"partiallyPaid\": %d, \"overdue\": %d, \"cancelled\": %d }",
                distId, total, draft, unpaid, sent, paid, partiallyPaid, overdue, cancelled
            );
        } catch (Exception e) {
            log.error("InvoiceTool: error for distributorId '{}': {}", distributorId, e.getMessage());
            return "{ \"error\": \"Failed to retrieve invoice summary: " + e.getMessage() + "\" }";
        }
    }
}
