package com.zuqi.service.impl;

import com.zuqi.api.dto.aging.*;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.procurement.PurchaseOrder;
import com.zuqi.repository.InvoiceRepository;
import com.zuqi.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgingReportServiceImpl implements com.zuqi.service.AgingReportService {

    private final InvoiceRepository invoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    public ArAgingResponse getArAging(UUID distributorId, LocalDate asOfDate) {
        List<Invoice> invoices = invoiceRepository.findUnpaidByDistributorId(distributorId);
        List<ArAgingRow> rows = new ArrayList<>();

        BigDecimal sumCurrent = BigDecimal.ZERO, sum1 = BigDecimal.ZERO,
                sum2 = BigDecimal.ZERO, sum3 = BigDecimal.ZERO, sum4 = BigDecimal.ZERO;

        for (Invoice inv : invoices) {
            BigDecimal balance = inv.getBalanceDue() != null ? inv.getBalanceDue() : inv.getTotalAmount();
            if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) continue;

            long daysOverdue = ChronoUnit.DAYS.between(inv.getDueDate(), asOfDate);
            BigDecimal cur = BigDecimal.ZERO, b1 = BigDecimal.ZERO,
                    b2 = BigDecimal.ZERO, b3 = BigDecimal.ZERO, b4 = BigDecimal.ZERO;

            if (daysOverdue <= 0) {
                cur = balance;
                sumCurrent = sumCurrent.add(balance);
            } else if (daysOverdue <= 30) {
                b1 = balance;
                sum1 = sum1.add(balance);
            } else if (daysOverdue <= 60) {
                b2 = balance;
                sum2 = sum2.add(balance);
            } else if (daysOverdue <= 90) {
                b3 = balance;
                sum3 = sum3.add(balance);
            } else {
                b4 = balance;
                sum4 = sum4.add(balance);
            }

            rows.add(ArAgingRow.builder()
                    .customerId(inv.getMerchant() != null ? inv.getMerchant().getId() : null)
                    .customerName(inv.getMerchant() != null ? inv.getMerchant().getBusinessName() : "N/A")
                    .invoiceId(inv.getId())
                    .invoiceNumber(inv.getInvoiceNumber())
                    .issueDate(inv.getIssueDate())
                    .dueDate(inv.getDueDate())
                    .totalAmount(inv.getTotalAmount())
                    .balanceDue(balance)
                    .daysOverdue(Math.max(daysOverdue, 0))
                    .current(cur).bucket1(b1).bucket2(b2).bucket3(b3).bucket4(b4)
                    .build());
        }

        BigDecimal total = sumCurrent.add(sum1).add(sum2).add(sum3).add(sum4);
        return ArAgingResponse.builder()
                .asOfDate(asOfDate)
                .rows(rows)
                .summary(AgingBucketSummary.builder()
                        .current(sumCurrent).bucket1(sum1).bucket2(sum2)
                        .bucket3(sum3).bucket4(sum4).total(total)
                        .build())
                .build();
    }

    @Override
    public ApAgingResponse getApAging(UUID distributorId, LocalDate asOfDate) {
        List<PurchaseOrder> pos = purchaseOrderRepository.findOutstandingByDistributorId(distributorId);
        List<ApAgingRow> rows = new ArrayList<>();

        BigDecimal sumCurrent = BigDecimal.ZERO, sum1 = BigDecimal.ZERO,
                sum2 = BigDecimal.ZERO, sum3 = BigDecimal.ZERO, sum4 = BigDecimal.ZERO;

        for (PurchaseOrder po : pos) {
            BigDecimal amount = po.getTotalAmount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Compute due date from payment terms
            int paymentDays = po.getPaymentTermsDays() != null ? po.getPaymentTermsDays() : 30;
            LocalDate dueDate;
            if (po.getExpectedDeliveryDate() != null) {
                dueDate = po.getExpectedDeliveryDate().plusDays(paymentDays);
            } else {
                dueDate = po.getCreatedAt().toLocalDate().plusDays(paymentDays);
            }

            long daysOverdue = ChronoUnit.DAYS.between(dueDate, asOfDate);
            BigDecimal cur = BigDecimal.ZERO, b1 = BigDecimal.ZERO,
                    b2 = BigDecimal.ZERO, b3 = BigDecimal.ZERO, b4 = BigDecimal.ZERO;

            if (daysOverdue <= 0) {
                cur = amount;
                sumCurrent = sumCurrent.add(amount);
            } else if (daysOverdue <= 30) {
                b1 = amount;
                sum1 = sum1.add(amount);
            } else if (daysOverdue <= 60) {
                b2 = amount;
                sum2 = sum2.add(amount);
            } else if (daysOverdue <= 90) {
                b3 = amount;
                sum3 = sum3.add(amount);
            } else {
                b4 = amount;
                sum4 = sum4.add(amount);
            }

            rows.add(ApAgingRow.builder()
                    .supplierId(po.getSupplier().getId())
                    .supplierName(po.getSupplier().getName())
                    .purchaseOrderId(po.getId())
                    .poNumber(po.getPoNumber())
                    .orderDate(po.getCreatedAt().toLocalDate())
                    .dueDate(dueDate)
                    .totalAmount(amount)
                    .daysOverdue(Math.max(daysOverdue, 0))
                    .current(cur).bucket1(b1).bucket2(b2).bucket3(b3).bucket4(b4)
                    .build());
        }

        BigDecimal total = sumCurrent.add(sum1).add(sum2).add(sum3).add(sum4);
        return ApAgingResponse.builder()
                .asOfDate(asOfDate)
                .rows(rows)
                .summary(AgingBucketSummary.builder()
                        .current(sumCurrent).bucket1(sum1).bucket2(sum2)
                        .bucket3(sum3).bucket4(sum4).total(total)
                        .build())
                .build();
    }
}
