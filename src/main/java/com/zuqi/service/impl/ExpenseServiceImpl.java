package com.zuqi.service.impl;

import com.zuqi.api.dto.approval.CreateApprovalRequestDto;
import com.zuqi.api.dto.expense.ExpenseRequest;
import com.zuqi.api.dto.expense.ExpenseResponse;
import com.zuqi.api.dto.gl.JournalEntryResponse;
import com.zuqi.domain.approval.ApprovalWorkflowType;
import com.zuqi.domain.audit.ActivityAction;
import com.zuqi.domain.expense.Expense;
import com.zuqi.domain.expense.ExpenseStatus;
import com.zuqi.domain.user.User;
import com.zuqi.domain.gl.JournalSourceModule;
import com.zuqi.domain.gl.SystemAccountType;
import com.zuqi.domain.gl.GlAccount;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.repository.ExpenseRepository;
import com.zuqi.repository.GlAccountRepository;
import com.zuqi.service.ActivityLogService;
import com.zuqi.service.ApprovalService;
import com.zuqi.service.ExpenseService;
import com.zuqi.service.GlPostingService;
import com.zuqi.service.GlPostingService.PostingLine;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GlAccountRepository glAccountRepository;
    private final GlPostingService glPostingService;
    private final SecurityUtils securityUtils;
    private final ActivityLogService activityLogService;

    @Lazy
    @Autowired
    private ApprovalService approvalService;

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseResponse> getAll(ExpenseStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        boolean hasDates = startDate != null && endDate != null;
        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            if (status != null && hasDates)
                return expenseRepository.findByDistributorMerchantIdAndStatusAndDateRange(merchantId, status, startDate, endDate, pageable).map(ExpenseResponse::from);
            if (status != null)
                return expenseRepository.findByDistributorMerchantIdAndStatus(merchantId, status, pageable).map(ExpenseResponse::from);
            if (hasDates)
                return expenseRepository.findByDistributorMerchantIdAndDateRange(merchantId, startDate, endDate, pageable).map(ExpenseResponse::from);
            return expenseRepository.findByDistributorMerchantId(merchantId, pageable).map(ExpenseResponse::from);
        }

        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            if (status != null && hasDates)
                return expenseRepository.findByDistributorIdAndStatusAndExpenseDateBetweenOrderByExpenseDateDesc(distributorId, status, startDate, endDate, pageable).map(ExpenseResponse::from);
            if (status != null)
                return expenseRepository.findByDistributorIdAndStatusOrderByExpenseDateDesc(distributorId, status, pageable).map(ExpenseResponse::from);
            if (hasDates)
                return expenseRepository.findByDistributorIdAndExpenseDateBetweenOrderByExpenseDateDesc(distributorId, startDate, endDate, pageable).map(ExpenseResponse::from);
            return expenseRepository.findByDistributorIdOrderByExpenseDateDesc(distributorId, pageable).map(ExpenseResponse::from);
        }

        // SUPER_ADMIN
        if (hasDates)
            return expenseRepository.findByExpenseDateBetweenOrderByExpenseDateDesc(startDate, endDate, pageable).map(ExpenseResponse::from);
        return expenseRepository.findAll(pageable).map(ExpenseResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseResponse getById(UUID id) {
        return ExpenseResponse.from(findById(id));
    }

    @Override
    public ExpenseResponse create(UUID distributorId, ExpenseRequest request) {
        Expense expense = Expense.builder()
                .distributorId(distributorId)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .amount(request.getAmount())
                .expenseDate(request.getExpenseDate())
                .referenceNumber(request.getReferenceNumber())
                .receiptUrl(request.getReceiptUrl())
                .paymentMethod(request.getPaymentMethod())
                .status(ExpenseStatus.DRAFT)
                .createdBy(securityUtils.getCurrentUserId())
                .build();
        Expense savedExpense = expenseRepository.save(expense);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.CREATE, "EXPENSE", savedExpense.getId(),
                savedExpense.getTitle(), "EXPENSES", "Created expense: " + savedExpense.getTitle()
            );
        }
        return ExpenseResponse.from(savedExpense);
    }

    @Override
    public ExpenseResponse update(UUID id, ExpenseRequest request) {
        Expense expense = findById(id);
        if (expense.getStatus() == ExpenseStatus.APPROVED || expense.getStatus() == ExpenseStatus.PAID) {
            throw new IllegalStateException("Cannot modify an approved or paid expense");
        }
        // Editing a pending-approval expense retracts it back to DRAFT for re-submission
        if (expense.getStatus() == ExpenseStatus.PENDING_APPROVAL) {
            expense.setStatus(ExpenseStatus.DRAFT);
        }
        expense.setTitle(request.getTitle());
        expense.setDescription(request.getDescription());
        expense.setCategory(request.getCategory());
        expense.setAmount(request.getAmount());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setReferenceNumber(request.getReferenceNumber());
        expense.setReceiptUrl(request.getReceiptUrl());
        expense.setPaymentMethod(request.getPaymentMethod());
        Expense updatedExpense = expenseRepository.save(expense);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.UPDATE, "EXPENSE", updatedExpense.getId(),
                updatedExpense.getTitle(), "EXPENSES", "Updated expense: " + updatedExpense.getTitle()
            );
        }
        return ExpenseResponse.from(updatedExpense);
    }

    @Override
    public ExpenseResponse submit(UUID id) {
        Expense expense = findById(id);
        if (expense.getStatus() != ExpenseStatus.DRAFT && expense.getStatus() != ExpenseStatus.REJECTED) {
            throw new IllegalStateException("Only DRAFT or REJECTED expenses can be submitted");
        }

        boolean needsApproval = securityUtils.currentUserRequiresApprovalFor("EXPENSES");
        if (needsApproval) {
            expense.setStatus(ExpenseStatus.PENDING_APPROVAL);
            Expense saved = expenseRepository.save(expense);
            try {
                approvalService.createRequest(securityUtils.getCurrentUserId(),
                    CreateApprovalRequestDto.builder()
                        .workflowType(ApprovalWorkflowType.EXPENSE)
                        .entityType("EXPENSE")
                        .entityId(saved.getId())
                        .entityName(saved.getTitle())
                        .description("Expense: " + saved.getTitle() + " — KES " + saved.getAmount())
                        .requiredApprovals(1)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to create approval request for expense {}: {}", saved.getId(), e.getMessage());
            }
            return ExpenseResponse.from(saved);
        }

        expense.setStatus(ExpenseStatus.SUBMITTED);
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @Override
    public ExpenseResponse approve(UUID id) {
        Expense expense = findById(id);
        if (expense.getStatus() != ExpenseStatus.SUBMITTED && expense.getStatus() != ExpenseStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only SUBMITTED or PENDING_APPROVAL expenses can be approved");
        }
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setApprovedBy(securityUtils.getCurrentUserId());
        expense.setApprovedAt(LocalDateTime.now());
        Expense saved = expenseRepository.save(expense);

        // GL auto-posting: DR OTHER_EXPENSE / CR ACCOUNTS_PAYABLE
        postExpenseApproved(saved);

        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.APPROVE, "EXPENSE", saved.getId(),
                saved.getTitle(), "EXPENSES", "Approved expense: " + saved.getTitle()
            );
        }

        return ExpenseResponse.from(saved);
    }

    @Override
    public ExpenseResponse reject(UUID id, String reason) {
        Expense expense = findById(id);
        if (expense.getStatus() != ExpenseStatus.SUBMITTED && expense.getStatus() != ExpenseStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only SUBMITTED or PENDING_APPROVAL expenses can be rejected");
        }
        expense.setStatus(ExpenseStatus.REJECTED);
        // Store reason in description if provided
        if (reason != null && !reason.isBlank()) {
            expense.setDescription(
                    (expense.getDescription() != null ? expense.getDescription() + "\n" : "") +
                    "[REJECTED] " + reason);
        }
        Expense rejectedExpense = expenseRepository.save(expense);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.REJECT, "EXPENSE", rejectedExpense.getId(),
                rejectedExpense.getTitle(), "EXPENSES", "Rejected expense: " + rejectedExpense.getTitle()
            );
        }
        return ExpenseResponse.from(rejectedExpense);
    }

    @Override
    public ExpenseResponse markPaid(UUID id) {
        Expense expense = findById(id);
        if (expense.getStatus() != ExpenseStatus.APPROVED) {
            throw new IllegalStateException("Only APPROVED expenses can be marked as paid");
        }
        expense.setStatus(ExpenseStatus.PAID);
        expense.setPaidAt(LocalDateTime.now());
        Expense saved = expenseRepository.save(expense);

        // GL posting: DR ACCOUNTS_PAYABLE / CR CASH_AND_BANK
        postExpensePaid(saved);

        return ExpenseResponse.from(saved);
    }

    @Override
    public void delete(UUID id) {
        Expense expense = findById(id);
        if (expense.getStatus() == ExpenseStatus.APPROVED || expense.getStatus() == ExpenseStatus.PAID) {
            throw new IllegalStateException("Cannot delete an approved or paid expense");
        }
        expenseRepository.delete(expense);
        User currentUser = securityUtils.getCurrentUser();
        if (currentUser != null) {
            activityLogService.log(
                currentUser.getId(), currentUser.getEmail(),
                currentUser.getFirstName() + " " + currentUser.getLastName(),
                ActivityAction.DELETE, "EXPENSE", expense.getId(),
                expense.getTitle(), "EXPENSES", "Deleted expense: " + expense.getTitle()
            );
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Expense findById(UUID id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", "id", id));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void postExpenseApproved(Expense expense) {
        UUID distId = expense.getDistributorId();
        Optional<GlAccount> expenseAcct = glAccountRepository
                .findByDistributorIdAndSystemAccountType(distId, SystemAccountType.OTHER_EXPENSE);
        Optional<GlAccount> ap = glAccountRepository
                .findByDistributorIdAndSystemAccountType(distId, SystemAccountType.ACCOUNTS_PAYABLE);

        if (expenseAcct.isEmpty() || ap.isEmpty()) {
            log.debug("GL auto-post skipped (expense approved): accounts not configured for distributor {}", distId);
            return;
        }

        try {
            JournalEntryResponse entry = glPostingService.post(
                    distId,
                    JournalSourceModule.TREASURY,
                    expense.getId(),
                    expense.getExpenseDate(),
                    "Expense: " + expense.getTitle(),
                    expense.getReferenceNumber(),
                    List.of(
                            PostingLine.builder()
                                    .accountId(expenseAcct.get().getId())
                                    .description("Expense — " + expense.getTitle())
                                    .debitAmount(expense.getAmount())
                                    .creditAmount(BigDecimal.ZERO)
                                    .build(),
                            PostingLine.builder()
                                    .accountId(ap.get().getId())
                                    .description("AP — " + expense.getTitle())
                                    .debitAmount(BigDecimal.ZERO)
                                    .creditAmount(expense.getAmount())
                                    .build()
                    ),
                    null
            );
            // Link GL entry back to expense in the outer transaction via expenseRepository
            expenseRepository.findById(expense.getId()).ifPresent(e -> {
                e.setGlEntryId(entry.getId());
                expenseRepository.save(e);
            });
        } catch (Exception ex) {
            log.warn("GL auto-post failed (expense approved) for {}: {}", expense.getId(), ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void postExpensePaid(Expense expense) {
        UUID distId = expense.getDistributorId();
        Optional<GlAccount> ap = glAccountRepository
                .findByDistributorIdAndSystemAccountType(distId, SystemAccountType.ACCOUNTS_PAYABLE);
        Optional<GlAccount> cash = glAccountRepository
                .findByDistributorIdAndSystemAccountType(distId, SystemAccountType.CASH_AND_BANK);

        if (ap.isEmpty() || cash.isEmpty()) {
            log.debug("GL auto-post skipped (expense paid): accounts not configured for distributor {}", distId);
            return;
        }

        try {
            glPostingService.post(
                    distId,
                    JournalSourceModule.TREASURY,
                    expense.getId(),
                    LocalDate.now(),
                    "Expense paid: " + expense.getTitle(),
                    expense.getReferenceNumber(),
                    List.of(
                            PostingLine.builder()
                                    .accountId(ap.get().getId())
                                    .description("AP cleared — " + expense.getTitle())
                                    .debitAmount(expense.getAmount())
                                    .creditAmount(BigDecimal.ZERO)
                                    .build(),
                            PostingLine.builder()
                                    .accountId(cash.get().getId())
                                    .description("Cash paid — " + expense.getTitle())
                                    .debitAmount(BigDecimal.ZERO)
                                    .creditAmount(expense.getAmount())
                                    .build()
                    ),
                    null
            );
        } catch (Exception ex) {
            log.warn("GL auto-post failed (expense paid) for {}: {}", expense.getId(), ex.getMessage());
        }
    }
}
