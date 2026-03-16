package com.zuqi.service.impl;

import com.zuqi.domain.gl.JournalSourceModule;
import com.zuqi.domain.gl.SystemAccountType;
import com.zuqi.domain.gl.GlAccount;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceItem;
import com.zuqi.domain.pos.PosSale;
import com.zuqi.repository.GlAccountRepository;
import com.zuqi.service.GlAccountService;
import com.zuqi.service.GlAutoPostingService;
import com.zuqi.service.GlPeriodService;
import com.zuqi.service.GlPostingService;
import com.zuqi.service.GlPostingService.PostingLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlAutoPostingServiceImpl implements GlAutoPostingService {

    private final GlAccountRepository glAccountRepository;
    private final GlPostingService glPostingService;
    private final GlAccountService glAccountService;
    private final GlPeriodService glPeriodService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postInvoiceCreated(Invoice invoice) {
        UUID distId = invoice.getDistributor().getId();
        LocalDate date = invoice.getIssueDate() != null ? invoice.getIssueDate() : LocalDate.now();
        ensureGlAccounts(distId);

        Optional<GlAccount> ar      = find(distId, SystemAccountType.ACCOUNTS_RECEIVABLE);
        Optional<GlAccount> revenue = find(distId, SystemAccountType.SALES_REVENUE);

        if (ar.isEmpty() || revenue.isEmpty()) {
            log.warn("GL auto-post skipped (invoice created): AR or Revenue account not configured for distributor {}", distId);
            return;
        }

        BigDecimal amount = invoice.getTotalAmount();
        try {
            glPostingService.post(
                    distId,
                    JournalSourceModule.SALES,
                    invoice.getId(),
                    date,
                    "Invoice " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(),
                    List.of(
                            debit(ar.get().getId(),       "Accounts Receivable — " + invoice.getInvoiceNumber(), amount),
                            credit(revenue.get().getId(), "Sales Revenue — "       + invoice.getInvoiceNumber(), amount)
                    ),
                    null
            );
        } catch (Exception e) {
            log.error("GL auto-post failed (invoice created) for {}", invoice.getInvoiceNumber(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postPaymentReceived(Invoice invoice, BigDecimal amount) {
        UUID distId = invoice.getDistributor().getId();
        ensureGlAccounts(distId);

        Optional<GlAccount> cash = find(distId, SystemAccountType.CASH_AND_BANK);
        Optional<GlAccount> ar   = find(distId, SystemAccountType.ACCOUNTS_RECEIVABLE);

        if (cash.isEmpty() || ar.isEmpty()) {
            log.warn("GL auto-post skipped (payment received): Cash or AR account not configured for distributor {}", distId);
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
            log.error("GL auto-post failed (payment received) for {}", invoice.getInvoiceNumber(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postPosSaleCompleted(PosSale sale) {
        UUID distId = sale.getBranch().getDistributor().getId();
        LocalDate saleDate = LocalDate.now();

        ensureGlAccounts(distId);

        Optional<GlAccount> ar             = find(distId, SystemAccountType.ACCOUNTS_RECEIVABLE);
        Optional<GlAccount> defaultRevenue = find(distId, SystemAccountType.SALES_REVENUE);

        if (ar.isEmpty() || defaultRevenue.isEmpty()) {
            log.warn("GL auto-post skipped (POS sale completed): AR or Revenue account not configured for distributor {}", distId);
            return;
        }

        BigDecimal totalAmount = sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO;
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) return;

        String ref = sale.getReceiptNumber() != null ? sale.getReceiptNumber() : "POS-" + sale.getId();

        // Build credit lines grouped by effective revenue account (product override or default)
        Map<UUID, BigDecimal> revenueByAccount = new java.util.LinkedHashMap<>();
        for (var item : sale.getItems()) {
            if (item.getLineTotal() == null || item.getLineTotal().compareTo(BigDecimal.ZERO) <= 0) continue;
            UUID accountId = (item.getProduct() != null && item.getProduct().getRevenueAccountId() != null)
                    ? item.getProduct().getRevenueAccountId()
                    : defaultRevenue.get().getId();
            revenueByAccount.merge(accountId, item.getLineTotal(), BigDecimal::add);
        }
        // Fallback: if items are empty or all zero, use sale total against default account
        if (revenueByAccount.isEmpty()) {
            revenueByAccount.put(defaultRevenue.get().getId(), totalAmount);
        }

        try {
            List<GlPostingService.PostingLine> lines = new java.util.ArrayList<>();
            lines.add(debit(ar.get().getId(), "POS AR — " + ref, totalAmount));
            revenueByAccount.forEach((acctId, amt) ->
                    lines.add(credit(acctId, "POS revenue — " + ref, amt)));

            glPostingService.post(distId, JournalSourceModule.SALES, sale.getId(), saleDate,
                    "POS Sale " + ref, ref, lines, null);
        } catch (Exception e) {
            log.error("GL auto-post failed (POS sale revenue) for {}", ref, e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postPosCogs(PosSale sale) {
        UUID distId = sale.getBranch().getDistributor().getId();
        LocalDate saleDate = LocalDate.now();

        Optional<GlAccount> defaultCogs = find(distId, SystemAccountType.COST_OF_GOODS_SOLD);
        Optional<GlAccount> inventory   = find(distId, SystemAccountType.INVENTORY);

        if (defaultCogs.isEmpty() || inventory.isEmpty()) {
            log.debug("GL COGS auto-post skipped: COGS or Inventory account not configured for distributor {}", distId);
            return;
        }

        // Group item costs by effective COGS account (product override or default)
        Map<UUID, BigDecimal> costByAccount = new java.util.LinkedHashMap<>();
        for (var i : sale.getItems()) {
            if (i.getProduct() == null || i.getProduct().getCostPrice() == null
                    || i.getQuantity() == null || i.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal itemCost = i.getProduct().getCostPrice().multiply(i.getQuantity());
            UUID accountId = i.getProduct().getCogsAccountId() != null
                    ? i.getProduct().getCogsAccountId()
                    : defaultCogs.get().getId();
            costByAccount.merge(accountId, itemCost, BigDecimal::add);
        }

        if (costByAccount.isEmpty()) return;

        BigDecimal totalCost = costByAccount.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalCost.compareTo(BigDecimal.ZERO) <= 0) return;

        String ref = sale.getReceiptNumber() != null ? sale.getReceiptNumber() : "POS-" + sale.getId();
        try {
            List<GlPostingService.PostingLine> lines = new java.util.ArrayList<>();
            costByAccount.forEach((acctId, amt) ->
                    lines.add(debit(acctId, "COGS — " + ref, amt)));
            lines.add(credit(inventory.get().getId(), "Inventory — " + ref, totalCost));

            glPostingService.post(distId, JournalSourceModule.SALES, sale.getId(), saleDate,
                    "COGS — POS " + ref, ref, lines, null);
        } catch (Exception e) {
            log.error("GL COGS auto-post failed for POS {}", ref, e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postPosSalePayment(PosSale sale, BigDecimal paymentAmount) {
        UUID distId = sale.getBranch().getDistributor().getId();
        ensureGlAccounts(distId);

        Optional<GlAccount> cash = find(distId, SystemAccountType.CASH_AND_BANK);
        Optional<GlAccount> ar   = find(distId, SystemAccountType.ACCOUNTS_RECEIVABLE);

        if (cash.isEmpty() || ar.isEmpty()) {
            log.warn("GL auto-post skipped (POS payment): Cash or AR account not configured for distributor {}", distId);
            return;
        }

        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) return;

        String ref = sale.getReceiptNumber() != null ? sale.getReceiptNumber() : sale.getId().toString();
        try {
            glPostingService.post(
                    distId,
                    JournalSourceModule.SALES,
                    sale.getId(),
                    LocalDate.now(),
                    "POS payment — " + ref,
                    ref,
                    List.of(
                            debit(cash.get().getId(), "POS cash received — " + ref, paymentAmount),
                            credit(ar.get().getId(),  "AR cleared — " + ref, paymentAmount)
                    ),
                    null
            );
        } catch (Exception e) {
            log.error("GL auto-post failed (POS payment) for {}", ref, e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postManualInvoiceCogs(Invoice invoice, List<InvoiceItem> items, UUID distributorId) {
        Optional<GlAccount> defaultCogs = find(distributorId, SystemAccountType.COST_OF_GOODS_SOLD);
        Optional<GlAccount> inventory   = find(distributorId, SystemAccountType.INVENTORY);

        if (defaultCogs.isEmpty() || inventory.isEmpty()) {
            log.debug("GL COGS auto-post skipped (manual invoice): COGS or Inventory account not configured for distributor {}", distributorId);
            return;
        }

        Map<UUID, BigDecimal> costByAccount = new java.util.LinkedHashMap<>();
        for (InvoiceItem item : items) {
            if (item.getProduct() == null || item.getProduct().getCostPrice() == null
                    || item.getQuantity() == null || item.getQuantity() <= 0) continue;
            BigDecimal itemCost = item.getProduct().getCostPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            UUID accountId = item.getProduct().getCogsAccountId() != null
                    ? item.getProduct().getCogsAccountId()
                    : defaultCogs.get().getId();
            costByAccount.merge(accountId, itemCost, BigDecimal::add);
        }

        if (costByAccount.isEmpty()) return;

        BigDecimal totalCost = costByAccount.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalCost.compareTo(BigDecimal.ZERO) <= 0) return;

        String ref = invoice.getInvoiceNumber();
        LocalDate date = invoice.getIssueDate() != null ? invoice.getIssueDate() : LocalDate.now();
        try {
            List<GlPostingService.PostingLine> lines = new java.util.ArrayList<>();
            costByAccount.forEach((acctId, amt) ->
                    lines.add(debit(acctId, "COGS — " + ref, amt)));
            lines.add(credit(inventory.get().getId(), "Inventory — " + ref, totalCost));

            glPostingService.post(distributorId, JournalSourceModule.SALES, invoice.getId(), date,
                    "COGS — Invoice " + ref, ref, lines, null);
            log.info("GL COGS posted for manual invoice {} — total cost {}", ref, totalCost);
        } catch (Exception e) {
            log.error("GL COGS auto-post failed for manual invoice {}", ref, e);
        }
    }

    private void ensureGlAccounts(UUID distId) {
        boolean hasAr      = glAccountRepository.findByDistributorIdAndSystemAccountType(distId, SystemAccountType.ACCOUNTS_RECEIVABLE).isPresent();
        boolean hasRevenue = glAccountRepository.findByDistributorIdAndSystemAccountType(distId, SystemAccountType.SALES_REVENUE).isPresent();

        if (!hasAr || !hasRevenue) {
            try {
                glAccountService.seedDefaultAccounts(distId, null);
                log.info("Auto-seeded/patched GL accounts for distributor {} (AR present={}, Revenue present={})", distId, hasAr, hasRevenue);
            } catch (Exception e) {
                log.error("Failed to auto-seed GL accounts for distributor {}", distId, e);
            }
        }
    }

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
