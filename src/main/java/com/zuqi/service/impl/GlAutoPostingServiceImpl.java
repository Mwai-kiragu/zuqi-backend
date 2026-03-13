package com.zuqi.service.impl;

import com.zuqi.domain.gl.JournalSourceModule;
import com.zuqi.domain.gl.SystemAccountType;
import com.zuqi.domain.gl.GlAccount;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.pos.PosSale;
import com.zuqi.repository.GlAccountRepository;
import com.zuqi.service.GlAutoPostingService;
import com.zuqi.service.GlPostingService;
import com.zuqi.service.GlPostingService.PostingLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlAutoPostingServiceImpl implements GlAutoPostingService {

    private final GlAccountRepository glAccountRepository;
    private final GlPostingService glPostingService;

    // ── Invoice Created ──────────────────────────────────────────────────────

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postInvoiceCreated(Invoice invoice) {
        UUID distId = invoice.getDistributor().getId();
        Optional<GlAccount> ar      = find(distId, SystemAccountType.ACCOUNTS_RECEIVABLE);
        Optional<GlAccount> revenue = find(distId, SystemAccountType.SALES_REVENUE);

        if (ar.isEmpty() || revenue.isEmpty()) {
            log.debug("GL auto-post skipped (invoice created): AR or Revenue account not configured for distributor {}", distId);
            return;
        }

        BigDecimal amount = invoice.getTotalAmount();
        try {
            glPostingService.post(
                    distId,
                    JournalSourceModule.SALES,
                    invoice.getId(),
                    invoice.getIssueDate(),
                    "Invoice " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(),
                    List.of(
                            debit(ar.get().getId(),      "Accounts Receivable — " + invoice.getInvoiceNumber(), amount),
                            credit(revenue.get().getId(), "Sales Revenue — "       + invoice.getInvoiceNumber(), amount)
                    ),
                    null
            );
        } catch (Exception e) {
            log.warn("GL auto-post failed (invoice created) for {}: {}", invoice.getInvoiceNumber(), e.getMessage());
        }
    }

    // ── Payment Received ─────────────────────────────────────────────────────

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postPaymentReceived(Invoice invoice, BigDecimal amount) {
        UUID distId = invoice.getDistributor().getId();
        Optional<GlAccount> cash = find(distId, SystemAccountType.CASH_AND_BANK);
        Optional<GlAccount> ar   = find(distId, SystemAccountType.ACCOUNTS_RECEIVABLE);

        if (cash.isEmpty() || ar.isEmpty()) {
            log.debug("GL auto-post skipped (payment received): Cash or AR account not configured for distributor {}", distId);
            return;
        }

        try {
            glPostingService.post(
                    distId,
                    JournalSourceModule.SALES,
                    invoice.getId(),
                    LocalDate.now(),
                    "Payment received — " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(),
                    List.of(
                            debit(cash.get().getId(), "Cash received — " + invoice.getInvoiceNumber(), amount),
                            credit(ar.get().getId(),  "AR cleared — "    + invoice.getInvoiceNumber(), amount)
                    ),
                    null
            );
        } catch (Exception e) {
            log.warn("GL auto-post failed (payment received) for {}: {}", invoice.getInvoiceNumber(), e.getMessage());
        }
    }

    // ── POS Sale Completed ───────────────────────────────────────────────────

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postPosSaleCompleted(PosSale sale) {
        UUID distId = sale.getBranch().getDistributor().getId();
        Optional<GlAccount> cash    = find(distId, SystemAccountType.CASH_AND_BANK);
        Optional<GlAccount> ar      = find(distId, SystemAccountType.ACCOUNTS_RECEIVABLE);
        Optional<GlAccount> revenue = find(distId, SystemAccountType.SALES_REVENUE);

        if (cash.isEmpty() || revenue.isEmpty()) {
            log.debug("GL auto-post skipped (POS sale): Cash or Revenue account not configured for distributor {}", distId);
            return;
        }

        BigDecimal totalAmount = sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal amountPaid  = sale.getAmountPaid()  != null ? sale.getAmountPaid()  : BigDecimal.ZERO;
        BigDecimal onCredit    = totalAmount.subtract(amountPaid);

        try {
            List<PostingLine> lines = new ArrayList<>();

            // Cash portion
            if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(debit(cash.get().getId(), "POS cash — " + sale.getReceiptNumber(), amountPaid));
            }

            // Credit portion goes to AR
            if (onCredit.compareTo(BigDecimal.ZERO) > 0 && ar.isPresent()) {
                lines.add(debit(ar.get().getId(), "POS credit — " + sale.getReceiptNumber(), onCredit));
            }

            // Revenue
            lines.add(credit(revenue.get().getId(), "POS revenue — " + sale.getReceiptNumber(), totalAmount));

            if (lines.size() < 2) return; // shouldn't happen, safety guard

            glPostingService.post(
                    distId,
                    JournalSourceModule.SALES,
                    sale.getId(),
                    LocalDate.now(),
                    "POS Sale " + sale.getReceiptNumber(),
                    sale.getReceiptNumber(),
                    lines,
                    null
            );

            // Optional COGS entry
            postCogs(sale, distId);

        } catch (Exception e) {
            log.warn("GL auto-post failed (POS sale) for {}: {}", sale.getReceiptNumber(), e.getMessage());
        }
    }

    // ── COGS helper ─────────────────────────────────────────────────────────

    private void postCogs(PosSale sale, UUID distId) {
        Optional<GlAccount> cogs      = find(distId, SystemAccountType.COST_OF_GOODS_SOLD);
        Optional<GlAccount> inventory = find(distId, SystemAccountType.INVENTORY);

        if (cogs.isEmpty() || inventory.isEmpty()) return;

        // Sum cost from sale items using product.costPrice × quantity
        BigDecimal totalCost = sale.getItems().stream()
                .filter(i -> i.getProduct() != null
                        && i.getProduct().getCostPrice() != null
                        && i.getQuantity() != null
                        && i.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(i -> i.getProduct().getCostPrice().multiply(i.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalCost.compareTo(BigDecimal.ZERO) <= 0) return;

        try {
            glPostingService.post(
                    distId,
                    JournalSourceModule.SALES,
                    sale.getId(),
                    LocalDate.now(),
                    "COGS — POS " + sale.getReceiptNumber(),
                    sale.getReceiptNumber(),
                    List.of(
                            debit(cogs.get().getId(),      "COGS — " + sale.getReceiptNumber(), totalCost),
                            credit(inventory.get().getId(), "Inventory — " + sale.getReceiptNumber(), totalCost)
                    ),
                    null
            );
        } catch (Exception e) {
            log.warn("GL COGS auto-post failed for POS {}: {}", sale.getReceiptNumber(), e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Optional<GlAccount> find(UUID distId, SystemAccountType type) {
        return glAccountRepository.findByDistributorIdAndSystemAccountType(distId, type);
    }

    private PostingLine debit(UUID accountId, String description, BigDecimal amount) {
        return PostingLine.builder()
                .accountId(accountId)
                .description(description)
                .debitAmount(amount)
                .creditAmount(BigDecimal.ZERO)
                .build();
    }

    private PostingLine credit(UUID accountId, String description, BigDecimal amount) {
        return PostingLine.builder()
                .accountId(accountId)
                .description(description)
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(amount)
                .build();
    }
}
