package com.zuqi.service.impl;

import com.zuqi.api.dto.accounting.BankReconciliationRequest;
import com.zuqi.api.dto.accounting.BankReconciliationResponse;
import com.zuqi.domain.accounting.BankReconciliation;
import com.zuqi.domain.accounting.BankReconciliationItem;
import com.zuqi.domain.accounting.BankReconciliationStatus;
import com.zuqi.domain.accounting.ReconciliationItemType;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.BankReconciliationRepository;
import com.zuqi.service.BankReconciliationService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BankReconciliationServiceImpl implements BankReconciliationService {

    private final BankReconciliationRepository reconciliationRepository;
    private final SecurityUtils securityUtils;

    @Override
    public BankReconciliationResponse create(UUID distributorId, BankReconciliationRequest request) {
        BankReconciliation recon = BankReconciliation.builder()
                .distributorId(distributorId)
                .accountName(request.getAccountName())
                .accountNumber(request.getAccountNumber())
                .bankName(request.getBankName())
                .statementDate(request.getStatementDate())
                .statementBalance(request.getStatementBalance())
                .systemBalance(request.getSystemBalance())
                .notes(request.getNotes())
                .createdBy(securityUtils.getCurrentUserId())
                .status(BankReconciliationStatus.DRAFT)
                .build();

        addItems(recon, request);
        recalculate(recon);

        return BankReconciliationResponse.from(reconciliationRepository.save(recon));
    }

    @Override
    public BankReconciliationResponse update(UUID id, BankReconciliationRequest request) {
        BankReconciliation recon = findById(id);
        if (recon.getStatus() == BankReconciliationStatus.RECONCILED) {
            throw new IllegalStateException("Cannot modify a reconciled record");
        }

        recon.setAccountName(request.getAccountName());
        recon.setAccountNumber(request.getAccountNumber());
        recon.setBankName(request.getBankName());
        recon.setStatementDate(request.getStatementDate());
        recon.setStatementBalance(request.getStatementBalance());
        recon.setSystemBalance(request.getSystemBalance());
        recon.setNotes(request.getNotes());
        recon.getItems().clear();
        addItems(recon, request);
        recalculate(recon);

        return BankReconciliationResponse.from(reconciliationRepository.save(recon));
    }

    @Override
    public BankReconciliationResponse uploadReceipt(UUID id, String receiptDataUri) {
        BankReconciliation recon = findById(id);
        recon.setReceiptImageUrl(receiptDataUri);
        if (recon.getStatus() == BankReconciliationStatus.DRAFT) {
            recon.setStatus(BankReconciliationStatus.IN_PROGRESS);
        }
        return BankReconciliationResponse.from(reconciliationRepository.save(recon));
    }

    @Override
    public BankReconciliationResponse reconcile(UUID id) {
        BankReconciliation recon = findById(id);
        recalculate(recon);
        recon.setStatus(BankReconciliationStatus.RECONCILED);
        recon.setReconciledBy(securityUtils.getCurrentUserId());
        recon.setReconciledAt(LocalDateTime.now());
        return BankReconciliationResponse.from(reconciliationRepository.save(recon));
    }

    @Override
    @Transactional(readOnly = true)
    public BankReconciliationResponse getById(UUID id) {
        return BankReconciliationResponse.from(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BankReconciliationResponse> getAll(UUID distributorId, Pageable pageable) {
        return reconciliationRepository
                .findByDistributorIdOrderByStatementDateDesc(distributorId, pageable)
                .map(BankReconciliationResponse::from);
    }

    @Override
    public void delete(UUID id) {
        BankReconciliation recon = findById(id);
        if (recon.getStatus() == BankReconciliationStatus.RECONCILED) {
            throw new IllegalStateException("Cannot delete a reconciled record");
        }
        reconciliationRepository.delete(recon);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private BankReconciliation findById(UUID id) {
        return reconciliationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BankReconciliation", "id", id));
    }

    private void addItems(BankReconciliation recon, BankReconciliationRequest request) {
        if (request.getItems() == null) return;
        for (var itemReq : request.getItems()) {
            BankReconciliationItem item = BankReconciliationItem.builder()
                    .reconciliation(recon)
                    .itemType(itemReq.getItemType())
                    .description(itemReq.getDescription())
                    .amount(itemReq.getAmount())
                    .transactionDate(itemReq.getTransactionDate())
                    .reference(itemReq.getReference())
                    .isCleared(itemReq.isCleared())
                    .build();
            recon.getItems().add(item);
        }
    }

    private void recalculate(BankReconciliation recon) {
        // Adjusted bank balance = statementBalance + deposits in transit - outstanding checks + bank errors
        BigDecimal adjustedBank = recon.getStatementBalance();
        BigDecimal adjustedSystem = recon.getSystemBalance();

        for (BankReconciliationItem item : recon.getItems()) {
            switch (item.getItemType()) {
                case DEPOSIT_IN_TRANSIT -> adjustedBank = adjustedBank.add(item.getAmount());
                case OUTSTANDING_CHECK -> adjustedBank = adjustedBank.subtract(item.getAmount());
                case BANK_ERROR -> adjustedBank = adjustedBank.add(item.getAmount());
                case BANK_FEE -> adjustedSystem = adjustedSystem.subtract(item.getAmount());
                case INTEREST_EARNED -> adjustedSystem = adjustedSystem.add(item.getAmount());
                case BOOK_ERROR -> adjustedSystem = adjustedSystem.add(item.getAmount());
                default -> {}
            }
        }

        recon.setAdjustedBankBalance(adjustedBank);
        recon.setAdjustedSystemBalance(adjustedSystem);
        recon.setDifference(adjustedBank.subtract(adjustedSystem));

        if (recon.getDifference().compareTo(BigDecimal.ZERO) == 0
                && recon.getStatus() != BankReconciliationStatus.RECONCILED) {
            recon.setStatus(BankReconciliationStatus.IN_PROGRESS);
        }
    }
}
