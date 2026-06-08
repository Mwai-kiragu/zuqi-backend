package com.zuqi.service.impl;

import com.zuqi.api.dto.returns.ApplyCreditNoteRequest;
import com.zuqi.api.dto.returns.CreditNoteResponse;
import com.zuqi.domain.invoice.Invoice;
import com.zuqi.domain.invoice.InvoiceStatus;
import com.zuqi.domain.returns.CreditNote;
import com.zuqi.domain.returns.CreditNoteApplication;
import com.zuqi.domain.returns.CreditNoteStatus;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.CreditNoteApplicationRepository;
import com.zuqi.repository.CreditNoteRepository;
import com.zuqi.repository.InvoiceRepository;
import com.zuqi.service.CreditNoteService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditNoteServiceImpl implements CreditNoteService {

    private final CreditNoteRepository            creditNoteRepository;
    private final CreditNoteApplicationRepository applicationRepository;
    private final InvoiceRepository               invoiceRepository;
    private final SecurityUtils                   securityUtils;

    @Override
    public Page<CreditNoteResponse> getAll(Pageable pageable) {
        UUID distId     = securityUtils.getDistributorIdForFiltering();
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (distId != null) {
            return creditNoteRepository.findByDistributorId(distId, pageable).map(this::toResponse);
        } else if (merchantId != null) {
            return creditNoteRepository.findByDistributorMerchantId(merchantId, pageable).map(this::toResponse);
        }
        return creditNoteRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    public CreditNoteResponse getById(UUID id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public CreditNoteResponse getBySalesReturn(UUID salesReturnId) {
        CreditNote cn = creditNoteRepository.findBySalesReturnId(salesReturnId)
                .orElseThrow(() -> new ResourceNotFoundException("CreditNote", "salesReturnId", salesReturnId));
        return toResponse(cn);
    }

    @Override
    @Transactional
    public CreditNoteResponse apply(UUID creditNoteId, ApplyCreditNoteRequest request) {
        CreditNote cn = findOrThrow(creditNoteId);

        if (cn.getStatus() == CreditNoteStatus.FULLY_APPLIED
                || cn.getStatus() == CreditNoteStatus.REFUNDED
                || cn.getStatus() == CreditNoteStatus.EXPIRED) {
            throw new ValidationException("Credit note " + cn.getCreditNoteNumber() + " has no remaining balance (status: " + cn.getStatus() + ")");
        }
        if (cn.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Credit note " + cn.getCreditNoteNumber() + " has no remaining balance");
        }
        if (request.getAmount().compareTo(cn.getRemainingAmount()) > 0) {
            throw new ValidationException(
                    "Application amount KES " + request.getAmount() +
                    " exceeds remaining credit balance of KES " + cn.getRemainingAmount());
        }

        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", request.getInvoiceId()));
        if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ValidationException("Cannot apply credit to a " + invoice.getStatus().name().toLowerCase() + " invoice");
        }

        // Apply the credit to the invoice
        invoice.applyCredit(request.getAmount());
        invoiceRepository.save(invoice);

        // Deduct from credit note balance
        BigDecimal newRemaining = cn.getRemainingAmount().subtract(request.getAmount());
        cn.setRemainingAmount(newRemaining);
        cn.setStatus(newRemaining.compareTo(BigDecimal.ZERO) == 0
                ? CreditNoteStatus.FULLY_APPLIED
                : CreditNoteStatus.PARTIALLY_APPLIED);

        // Record the application
        CreditNoteApplication app = CreditNoteApplication.builder()
                .creditNote(cn)
                .invoice(invoice)
                .amountApplied(request.getAmount())
                .appliedBy(securityUtils.getCurrentUser())
                .build();
        applicationRepository.save(app);
        cn.getApplications().add(app);

        CreditNote saved = creditNoteRepository.save(cn);
        log.info("Applied KES {} from credit note {} to invoice {} — remaining: KES {}",
                request.getAmount(), cn.getCreditNoteNumber(),
                invoice.getInvoiceNumber(), newRemaining);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CreditNoteResponse markRefunded(UUID creditNoteId) {
        CreditNote cn = findOrThrow(creditNoteId);
        if (cn.getStatus() == CreditNoteStatus.FULLY_APPLIED || cn.getStatus() == CreditNoteStatus.REFUNDED) {
            throw new ValidationException("Credit note is already " + cn.getStatus().name().toLowerCase());
        }
        cn.setStatus(CreditNoteStatus.REFUNDED);
        cn.setRemainingAmount(BigDecimal.ZERO);
        log.info("Credit note {} marked as REFUNDED", cn.getCreditNoteNumber());
        return toResponse(creditNoteRepository.save(cn));
    }

    private CreditNote findOrThrow(UUID id) {
        return creditNoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CreditNote", "id", id));
    }

    private CreditNoteResponse toResponse(CreditNote cn) {
        List<CreditNoteResponse.ApplicationResponse> apps = cn.getApplications().stream()
                .map(a -> CreditNoteResponse.ApplicationResponse.builder()
                        .id(a.getId())
                        .invoiceId(a.getInvoice().getId())
                        .invoiceNumber(a.getInvoice().getInvoiceNumber())
                        .amountApplied(a.getAmountApplied())
                        .appliedAt(a.getAppliedAt())
                        .build())
                .collect(Collectors.toList());

        return CreditNoteResponse.builder()
                .id(cn.getId())
                .creditNoteNumber(cn.getCreditNoteNumber())
                .distributorId(cn.getDistributor().getId())
                .customerId(cn.getCustomer() != null ? cn.getCustomer().getId() : null)
                .customerName(cn.getCustomer() != null ? cn.getCustomer().getBusinessName() : null)
                .salesReturnId(cn.getSalesReturn() != null ? cn.getSalesReturn().getId() : null)
                .salesReturnNumber(cn.getSalesReturn() != null ? cn.getSalesReturn().getReturnNumber() : null)
                .sourceInvoiceId(cn.getSourceInvoice() != null ? cn.getSourceInvoice().getId() : null)
                .sourceInvoiceNumber(cn.getSourceInvoice() != null ? cn.getSourceInvoice().getInvoiceNumber() : null)
                .amount(cn.getAmount())
                .remainingAmount(cn.getRemainingAmount())
                .status(cn.getStatus().name())
                .notes(cn.getNotes())
                .expiresAt(cn.getExpiresAt())
                .applications(apps)
                .createdAt(cn.getCreatedAt())
                .updatedAt(cn.getUpdatedAt())
                .build();
    }
}
