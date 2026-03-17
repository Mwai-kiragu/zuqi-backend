package com.zuqi.ai.agent.tools;

import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.repository.InvoiceRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceTool {

    private final InvoiceRepository invoiceRepository;

    @Tool("Get invoice summary for a distributor. Returns counts by status (DRAFT, UNPAID, SENT, PAID, " +
         "PARTIALLY_PAID, OVERDUE, CANCELLED), and the top 5 overdue invoices with merchant name, " +
         "invoice number, balance due, and due date. " +
         "Use for questions about invoices, billing, outstanding amounts, overdue invoices.")
    @Transactional(readOnly = true)
    public String getInvoiceSummary(@P("The distributor UUID") String distributorId) {
        log.info("[TOOL CALLED] getInvoiceSummary distributorId={}", distributorId);
        try {
            UUID distId = UUID.fromString(distributorId.trim());

            long total         = invoiceRepository.countByDistributorId(distId);
            long draft         = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.DRAFT);
            long unpaid        = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.UNPAID);
            long sent          = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.SENT);
            long paid          = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.PAID);
            long partiallyPaid = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.PARTIALLY_PAID);
            long overdue       = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.OVERDUE);
            long cancelled     = invoiceRepository.countByDistributorIdAndStatus(distId, InvoiceStatus.CANCELLED);

            // Top 5 overdue invoices with details
            List<Invoice> overdueInvoices = invoiceRepository
                    .findByDistributorIdAndStatus(distId, InvoiceStatus.OVERDUE, PageRequest.of(0, 5))
                    .getContent();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "{ \"tool\": \"InvoiceSummary\", \"distributorId\": \"%s\", " +
                "\"totalInvoices\": %d, \"draft\": %d, \"unpaid\": %d, \"sent\": %d, " +
                "\"paid\": %d, \"partiallyPaid\": %d, \"overdue\": %d, \"cancelled\": %d, ",
                distId, total, draft, unpaid, sent, paid, partiallyPaid, overdue, cancelled));

            sb.append("\"overdueInvoices\": [");
            for (int i = 0; i < overdueInvoices.size(); i++) {
                Invoice inv = overdueInvoices.get(i);
                String merchantName = inv.getMerchant() != null
                        ? inv.getMerchant().getBusinessName().replace("\"", "'") : "Unknown";
                String balanceDue = inv.getBalanceDue() != null ? inv.getBalanceDue().toPlainString() : "0";
                String dueDate    = inv.getDueDate() != null ? inv.getDueDate().toString() : "unknown";
                String invoiceNum = inv.getInvoiceNumber() != null ? inv.getInvoiceNumber() : "N/A";
                sb.append(String.format(
                        "{ \"merchant\": \"%s\", \"invoiceNumber\": \"%s\", \"balanceDueKES\": \"%s\", \"dueDate\": \"%s\" }",
                        merchantName, invoiceNum, balanceDue, dueDate));
                if (i < overdueInvoices.size() - 1) sb.append(", ");
            }
            sb.append("] }");

            return sb.toString();
        } catch (Exception e) {
            log.error("InvoiceTool: error for distributorId '{}': {}", distributorId, e.getMessage());
            return "{ \"error\": \"Failed to retrieve invoice summary: " + e.getMessage() + "\" }";
        }
    }
}
