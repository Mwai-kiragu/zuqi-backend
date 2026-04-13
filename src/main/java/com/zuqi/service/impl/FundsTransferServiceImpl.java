package com.zuqi.service.impl;

import com.zuqi.api.dto.ft.*;
import com.zuqi.domain.ft.*;
import com.zuqi.domain.procurement.GrnStatus;
import com.zuqi.domain.supplier.Supplier;
import com.zuqi.domain.supplier.SupplierBill;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.FundsTransferService;
import com.zuqi.service.GlAutoPostingService;
import com.zuqi.service.SupplierBillService;
import com.zuqi.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FundsTransferServiceImpl implements FundsTransferService {

    private final FundsTransferRepository fundsTransferRepository;
    private final FtAmountRangeRepository amountRangeRepository;
    private final FtApprovalLevelRepository approvalLevelRepository;
    private final FtApprovalRepository approvalRepository;
    private final ExpenseRepository expenseRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierBillRepository supplierBillRepository;
    private final GlAutoPostingService glAutoPostingService;
    private final SupplierBillService supplierBillService;
    private final GoodsReceiptNoteRepository grnRepository;
    private final SecurityUtils securityUtils;

    // ── Helpers ─────────────────────────────────────────────────────────────

    private FundsTransfer findById(UUID id) {
        return fundsTransferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FundsTransfer", "id", id));
    }

    private String generateReference() {
        return "FT-" + System.currentTimeMillis();
    }

    private String resolveReferenceSummary(String referenceType, UUID referenceId) {
        if ("EXPENSE".equalsIgnoreCase(referenceType)) {
            return expenseRepository.findById(referenceId)
                    .map(e -> "Expense: " + e.getTitle() + " | " + e.getAmount())
                    .orElse("Expense (deleted)");
        }
        if ("PURCHASE_ORDER".equalsIgnoreCase(referenceType)) {
            return purchaseOrderRepository.findById(referenceId)
                    .map(po -> "PO: " + po.getPoNumber() + " | " + po.getTotalAmount())
                    .orElse("Purchase Order (deleted)");
        }
        return null;
    }

    private FundsTransferResponse enrich(FundsTransfer ft) {
        FundsTransferResponse resp = FundsTransferResponse.from(ft);

        // Approval levels (configured rules)
        if (ft.getAmountRangeId() != null) {
            List<FtApprovalLevelDto> levels = approvalLevelRepository
                    .findByAmountRangeIdOrderByLevelNumber(ft.getAmountRangeId())
                    .stream()
                    .map(al -> {
                        FtApprovalLevelDto dto = new FtApprovalLevelDto();
                        dto.setId(al.getId());
                        dto.setLevelNumber(al.getLevelNumber());
                        dto.setLevelName(al.getLevelName());
                        dto.setApproverUserId(al.getApproverUserId());
                        return dto;
                    }).collect(Collectors.toList());
            resp.setApprovalLevels(levels);
        }

        // Reference summary (linked expense or purchase order)
        if (ft.getReferenceType() != null && ft.getReferenceId() != null) {
            try {
                String summary = resolveReferenceSummary(ft.getReferenceType(), ft.getReferenceId());
                resp.setReferenceSummary(summary);
            } catch (Exception ignored) { /* silently skip if reference was deleted */ }
        }

        // Approval history (recorded decisions)
        List<FtApprovalDecisionDto> history = approvalRepository
                .findByTransferIdOrderByLevelNumberAscCreatedAtAsc(ft.getId())
                .stream()
                .map(a -> {
                    FtApprovalDecisionDto dto = new FtApprovalDecisionDto();
                    dto.setId(a.getId());
                    dto.setLevelNumber(a.getLevelNumber());
                    dto.setApproverId(a.getApproverId());
                    dto.setStatus(a.getStatus());
                    dto.setComment(a.getComment());
                    dto.setCreatedAt(a.getCreatedAt());
                    return dto;
                }).collect(Collectors.toList());
        resp.setApprovalHistory(history);

        return resp;
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<FundsTransferResponse> getAll(FundsTransferStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        boolean hasDates = startDate != null && endDate != null;
        LocalDateTime from = hasDates ? startDate.atStartOfDay() : null;
        LocalDateTime to = hasDates ? endDate.plusDays(1).atStartOfDay() : null;

        UUID merchantId = securityUtils.getCurrentUserMerchantId();
        if (merchantId != null) {
            if (status != null && hasDates)
                return fundsTransferRepository.findByDistributorMerchantIdAndStatusAndDateRange(merchantId, status, from, to, pageable).map(this::enrich);
            if (status != null)
                return fundsTransferRepository.findByDistributorMerchantIdAndStatus(merchantId, status, pageable).map(this::enrich);
            if (hasDates)
                return fundsTransferRepository.findByDistributorMerchantIdAndDateRange(merchantId, from, to, pageable).map(this::enrich);
            return fundsTransferRepository.findByDistributorMerchantId(merchantId, pageable).map(this::enrich);
        }

        UUID distributorId = securityUtils.getDistributorIdForFiltering();
        if (distributorId != null) {
            if (status != null && hasDates)
                return fundsTransferRepository.findByDistributorIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(distributorId, status, from, to, pageable).map(this::enrich);
            if (status != null)
                return fundsTransferRepository.findByDistributorIdAndStatusOrderByCreatedAtDesc(distributorId, status, pageable).map(this::enrich);
            if (hasDates)
                return fundsTransferRepository.findByDistributorIdAndCreatedAtBetweenOrderByCreatedAtDesc(distributorId, from, to, pageable).map(this::enrich);
            return fundsTransferRepository.findByDistributorIdOrderByCreatedAtDesc(distributorId, pageable).map(this::enrich);
        }

        // SUPER_ADMIN
        if (status != null && hasDates)
            return fundsTransferRepository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(status, from, to, pageable).map(this::enrich);
        if (hasDates)
            return fundsTransferRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, pageable).map(this::enrich);
        return fundsTransferRepository.findAll(pageable).map(this::enrich);
    }

    @Override
    @Transactional(readOnly = true)
    public FundsTransferResponse getById(UUID id) {
        return enrich(findById(id));
    }

    // ── Create / Update ───────────────────────────────────────────────────────

    @Override
    public FundsTransferResponse create(UUID distributorId, FundsTransferRequest req) {
        UUID initiatorId = securityUtils.getCurrentUserId();

        // Resolve supplier (for SUPPLIER_PAYMENT)
        Supplier supplier = null;
        String creditAccount = req.getCreditAccountNumber();
        String creditBank = req.getCreditBankName();

        if (req.getSupplierId() != null) {
            supplier = supplierRepository.findById(req.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", req.getSupplierId()));
            // Auto-fill bank details from supplier if not explicitly provided
            if ((creditAccount == null || creditAccount.isBlank()) && supplier.getBankAccountNumber() != null) {
                creditAccount = supplier.getBankAccountNumber();
            }
            if ((creditBank == null || creditBank.isBlank()) && supplier.getBankName() != null) {
                creditBank = supplier.getBankName();
            }
        }

        // Resolve supplier bill
        SupplierBill supplierBill = null;
        if (req.getSupplierBillId() != null) {
            supplierBill = supplierBillRepository.findById(req.getSupplierBillId())
                    .orElseThrow(() -> new ResourceNotFoundException("SupplierBill", "id", req.getSupplierBillId()));
        }

        FundsTransfer ft = FundsTransfer.builder()
                .distributorId(distributorId)
                .referenceNumber(generateReference())
                .transferType(req.getTransferType())
                .debitAccountNumber(req.getDebitAccountNumber())
                .debitBankName(req.getDebitBankName())
                .creditAccountNumber(creditAccount != null ? creditAccount : "")
                .creditBankName(creditBank)
                .amount(req.getAmount())
                .currency(req.getCurrency() != null ? req.getCurrency() : "KES")
                .description(req.getDescription())
                .paymentDetails(req.getPaymentDetails())
                .referenceType(req.getReferenceType())
                .referenceId(req.getReferenceId())
                .supplier(supplier)
                .supplierBill(supplierBill)
                .initiatorId(initiatorId)
                .status(FundsTransferStatus.DRAFT)
                .build();

        return enrich(fundsTransferRepository.save(ft));
    }

    @Override
    public FundsTransferResponse update(UUID id, FundsTransferRequest req) {
        FundsTransfer ft = findById(id);
        if (ft.getStatus() != FundsTransferStatus.DRAFT) {
            throw new ValidationException("Only DRAFT transfers can be edited");
        }
        ft.setTransferType(req.getTransferType());
        ft.setDebitAccountNumber(req.getDebitAccountNumber());
        ft.setDebitBankName(req.getDebitBankName());
        ft.setCreditAccountNumber(req.getCreditAccountNumber());
        ft.setCreditBankName(req.getCreditBankName());
        ft.setAmount(req.getAmount());
        if (req.getCurrency() != null) ft.setCurrency(req.getCurrency());
        ft.setDescription(req.getDescription());
        ft.setPaymentDetails(req.getPaymentDetails());
        ft.setReferenceType(req.getReferenceType());
        ft.setReferenceId(req.getReferenceId());
        return enrich(fundsTransferRepository.save(ft));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public FundsTransferResponse submit(UUID id) {
        FundsTransfer ft = findById(id);
        if (ft.getStatus() != FundsTransferStatus.DRAFT) {
            throw new ValidationException("Only DRAFT transfers can be submitted");
        }

        // Find matching amount range
        List<FtAmountRange> ranges = amountRangeRepository
                .findMatchingRange(ft.getDistributorId(), ft.getAmount());

        if (!ranges.isEmpty()) {
            FtAmountRange range = ranges.get(0);
            ft.setAmountRangeId(range.getId());
            ft.setRequiredApprovalLevels(range.getRequiredLevels());
        } else {
            ft.setRequiredApprovalLevels(1); // Default: 1 level required
        }

        ft.setCurrentApprovalLevel(1);
        ft.setStatus(FundsTransferStatus.PENDING_APPROVAL);
        return enrich(fundsTransferRepository.save(ft));
    }

    @Override
    public FundsTransferResponse approve(UUID id, String comment) {
        FundsTransfer ft = findById(id);
        if (ft.getStatus() != FundsTransferStatus.PENDING_APPROVAL) {
            throw new ValidationException("Transfer is not pending approval");
        }

        UUID approverId = securityUtils.getCurrentUserId();
        int level = ft.getCurrentApprovalLevel();

        // Maker-checker: submitter cannot approve their own transfer
        if (ft.getInitiatorId() != null && ft.getInitiatorId().equals(approverId)) {
            throw new ValidationException("You cannot approve a transfer you submitted");
        }

        // When no amount range configured, require an approval role
        if (ft.getAmountRangeId() == null) {
            boolean canApprove = securityUtils.currentUserHasRole("VERIFIER")
                    || securityUtils.currentUserHasRole("AUTHORIZER")
                    || securityUtils.currentUserHasRole("DISTRIBUTOR_ADMIN")
                    || securityUtils.currentUserHasRole("MERCHANT_ADMIN")
                    || securityUtils.currentUserHasRole("SUPER_ADMIN")
                    || securityUtils.currentUserHasRole("FINANCE");
            if (!canApprove) {
                throw new ValidationException("You are not authorised to approve funds transfers");
            }
        }

        // Prevent double-approving at the same level
        if (approvalRepository.findByTransferIdAndLevelNumberAndApproverId(ft.getId(), level, approverId).isPresent()) {
            throw new ValidationException("You have already acted on this transfer at level " + level);
        }

        // Prevent the same person from approving multiple levels (maker-checker across levels)
        boolean approvedPreviousLevel = approvalRepository.findByTransferIdAndApproverId(ft.getId(), approverId)
                .stream().anyMatch(a -> a.getLevelNumber() < level);
        if (approvedPreviousLevel) {
            throw new ValidationException("You have already approved a previous level for this transfer. A different approver is required.");
        }

        // If amount range is configured, validate approver is in the level rules
        if (ft.getAmountRangeId() != null) {
            List<FtApprovalLevel> levelRules = approvalLevelRepository
                    .findByAmountRangeIdAndLevelNumber(ft.getAmountRangeId(), level);
            boolean isAuthorised = levelRules.stream()
                    .anyMatch(r -> r.getApproverUserId().equals(approverId));
            // If no rules configured for this level, allow any admin to approve
            if (!levelRules.isEmpty() && !isAuthorised) {
                throw new ValidationException("You are not authorised to approve at level " + level);
            }
        }

        // Record approval
        approvalRepository.save(FtApproval.builder()
                .transferId(ft.getId())
                .levelNumber(level)
                .approverId(approverId)
                .status("APPROVED")
                .comment(comment)
                .build());

        // Count approvals at this level
        long approvedCount = approvalRepository.countByTransferIdAndLevelNumberAndStatus(ft.getId(), level, "APPROVED");

        // Determine required approvals: if rules defined, need all; else 1
        long required = 1;
        if (ft.getAmountRangeId() != null) {
            long ruleCount = approvalLevelRepository
                    .findByAmountRangeIdAndLevelNumber(ft.getAmountRangeId(), level).size();
            if (ruleCount > 0) required = ruleCount;
        }

        if (approvedCount >= required) {
            if (level >= ft.getRequiredApprovalLevels()) {
                // All levels passed → APPROVED
                ft.setStatus(FundsTransferStatus.APPROVED);
            } else {
                // Advance to next level
                ft.setCurrentApprovalLevel(level + 1);
            }
        }

        return enrich(fundsTransferRepository.save(ft));
    }

    @Override
    public FundsTransferResponse reject(UUID id, String reason) {
        FundsTransfer ft = findById(id);
        if (ft.getStatus() != FundsTransferStatus.PENDING_APPROVAL) {
            throw new ValidationException("Transfer is not pending approval");
        }

        UUID approverId = securityUtils.getCurrentUserId();

        approvalRepository.save(FtApproval.builder()
                .transferId(ft.getId())
                .levelNumber(ft.getCurrentApprovalLevel())
                .approverId(approverId)
                .status("REJECTED")
                .comment(reason)
                .build());

        ft.setStatus(FundsTransferStatus.REJECTED);
        ft.setRejectedReason(reason);
        return enrich(fundsTransferRepository.save(ft));
    }

    @Override
    public FundsTransferResponse cancel(UUID id) {
        FundsTransfer ft = findById(id);
        if (ft.getStatus() == FundsTransferStatus.APPROVED || ft.getStatus() == FundsTransferStatus.DISBURSED) {
            throw new ValidationException("Approved or disbursed transfers cannot be cancelled");
        }
        ft.setStatus(FundsTransferStatus.CANCELLED);
        return enrich(fundsTransferRepository.save(ft));
    }

    @Override
    public FundsTransferResponse disburse(UUID id) {
        FundsTransfer ft = findById(id);
        if (ft.getStatus() != FundsTransferStatus.APPROVED) {
            throw new ValidationException("Only APPROVED transfers can be disbursed");
        }

        // 3-way match check: if linked bill has a PO, require at least one CONFIRMED GRN
        if (ft.getSupplierBill() != null && ft.getSupplierBill().getPurchaseOrder() != null) {
            UUID poId = ft.getSupplierBill().getPurchaseOrder().getId();
            boolean hasConfirmedGrn = grnRepository.findByPurchaseOrderId(poId)
                    .stream().anyMatch(grn -> GrnStatus.CONFIRMED.equals(grn.getStatus()));
            if (!hasConfirmedGrn) {
                throw new ValidationException(
                    "Payment cannot be disbursed: no confirmed Goods Receipt Note found for Purchase Order " + poId);
            }
        }

        ft.setStatus(FundsTransferStatus.DISBURSED);
        ft.setDisbursedAt(LocalDateTime.now());
        FundsTransfer saved = fundsTransferRepository.save(ft);

        // GL auto-post for supplier payments
        if (saved.getTransferType() == FundsTransferType.SUPPLIER_PAYMENT) {
            try {
                glAutoPostingService.postSupplierPaymentDisbursed(saved, saved.getAmount());
            } catch (Exception e) {
                log.warn("GL auto-post failed on disburse for FT {}: {}", saved.getReferenceNumber(), e.getMessage());
            }
            // Apply payment to linked supplier bill
            if (saved.getSupplierBill() != null) {
                try {
                    supplierBillService.applyPayment(saved.getSupplierBill().getId(), saved.getAmount());
                } catch (Exception e) {
                    log.warn("Failed to apply payment to supplier bill on disburse: {}", e.getMessage());
                }
            }
        }

        return enrich(saved);
    }

    // ── Amount Range Config ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<FtAmountRangeResponse> getAmountRanges(UUID distributorId) {
        return amountRangeRepository
                .findByDistributorIdAndIsActiveTrueOrderByMinAmountAsc(distributorId)
                .stream()
                .map(r -> {
                    FtAmountRangeResponse resp = FtAmountRangeResponse.from(r);
                    List<FtApprovalLevelDto> levels = approvalLevelRepository
                            .findByAmountRangeIdOrderByLevelNumber(r.getId())
                            .stream()
                            .map(al -> {
                                FtApprovalLevelDto dto = new FtApprovalLevelDto();
                                dto.setId(al.getId());
                                dto.setLevelNumber(al.getLevelNumber());
                                dto.setLevelName(al.getLevelName());
                                dto.setApproverUserId(al.getApproverUserId());
                                return dto;
                            }).collect(Collectors.toList());
                    resp.setApprovalLevels(levels);
                    return resp;
                }).collect(Collectors.toList());
    }

    @Override
    public FtAmountRangeResponse createAmountRange(UUID distributorId, FtAmountRangeRequest req) {
        FtAmountRange range = FtAmountRange.builder()
                .distributorId(distributorId)
                .name(req.getName())
                .minAmount(req.getMinAmount())
                .maxAmount(req.getMaxAmount())
                .requiredLevels(req.getRequiredLevels())
                .isActive(true)
                .build();
        range = amountRangeRepository.save(range);

        // Save approval level assignments if provided
        if (req.getApprovalLevels() != null) {
            final UUID rangeId = range.getId();
            req.getApprovalLevels().forEach(alReq ->
                    approvalLevelRepository.save(FtApprovalLevel.builder()
                            .amountRangeId(rangeId)
                            .levelNumber(alReq.getLevelNumber())
                            .levelName(alReq.getLevelName())
                            .approverUserId(alReq.getApproverUserId())
                            .build())
            );
        }

        final FtAmountRange savedRange = range;
        FtAmountRangeResponse resp = FtAmountRangeResponse.from(savedRange);
        resp.setApprovalLevels(getAmountRanges(distributorId).stream()
                .filter(r -> r.getId().equals(savedRange.getId()))
                .findFirst()
                .map(FtAmountRangeResponse::getApprovalLevels)
                .orElse(List.of()));
        return resp;
    }

    @Override
    public FtAmountRangeResponse updateAmountRange(UUID rangeId, FtAmountRangeRequest req) {
        FtAmountRange range = amountRangeRepository.findById(rangeId)
                .orElseThrow(() -> new ResourceNotFoundException("FtAmountRange", "id", rangeId));
        range.setName(req.getName());
        range.setMinAmount(req.getMinAmount());
        range.setMaxAmount(req.getMaxAmount());
        range.setRequiredLevels(req.getRequiredLevels());
        range = amountRangeRepository.save(range);

        // Replace approval level assignments
        if (req.getApprovalLevels() != null) {
            approvalLevelRepository.deleteByAmountRangeId(rangeId);
            final UUID rId = rangeId;
            req.getApprovalLevels().forEach(alReq ->
                    approvalLevelRepository.save(FtApprovalLevel.builder()
                            .amountRangeId(rId)
                            .levelNumber(alReq.getLevelNumber())
                            .levelName(alReq.getLevelName())
                            .approverUserId(alReq.getApproverUserId())
                            .build())
            );
        }

        FtAmountRangeResponse resp = FtAmountRangeResponse.from(range);
        List<FtApprovalLevelDto> levels = approvalLevelRepository
                .findByAmountRangeIdOrderByLevelNumber(rangeId)
                .stream()
                .map(al -> {
                    FtApprovalLevelDto dto = new FtApprovalLevelDto();
                    dto.setId(al.getId());
                    dto.setLevelNumber(al.getLevelNumber());
                    dto.setLevelName(al.getLevelName());
                    dto.setApproverUserId(al.getApproverUserId());
                    return dto;
                }).collect(Collectors.toList());
        resp.setApprovalLevels(levels);
        return resp;
    }

    @Override
    public void deleteAmountRange(UUID rangeId) {
        FtAmountRange range = amountRangeRepository.findById(rangeId)
                .orElseThrow(() -> new ResourceNotFoundException("FtAmountRange", "id", rangeId));
        range.setActive(false);
        amountRangeRepository.save(range);
    }
}
