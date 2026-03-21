package com.zuqi.service.impl;

import com.zuqi.api.dto.approval.TransactionalApprovalResponse;
import com.zuqi.api.dto.approval.TransactionalApprovalResponse.ApprovalRecordResponse;
import com.zuqi.domain.approval.ApprovalRecord;
import com.zuqi.domain.ft.FundsTransfer;
import com.zuqi.domain.inventory.StockTransfer;
import com.zuqi.domain.order.Order;
import com.zuqi.domain.supplier.SupplierBill;
import com.zuqi.domain.user.User;
import com.zuqi.exception.ResourceNotFoundException;
import com.zuqi.exception.ValidationException;
import com.zuqi.repository.*;
import com.zuqi.service.TransactionalApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionalApprovalServiceImpl implements TransactionalApprovalService {

    private static final String PENDING_VERIFIER   = "PENDING_VERIFIER";
    private static final String PENDING_AUTHORIZER = "PENDING_AUTHORIZER";
    private static final String APPROVED           = "APPROVED";
    private static final String REJECTED           = "REJECTED";
    private static final String NOT_REQUIRED       = "NOT_REQUIRED";

    private final ApprovalRecordRepository approvalRecordRepository;
    private final OrderRepository           orderRepository;
    private final StockTransferRepository   stockTransferRepository;
    private final SupplierBillRepository    supplierBillRepository;
    private final FundsTransferRepository   fundsTransferRepository;
    private final UserRepository            userRepository;

    @Override
    @Transactional
    public TransactionalApprovalResponse submit(String entityType, UUID entityId, UUID submittedById) {
        String currentStatus = getCurrentApprovalStatus(entityType, entityId);
        if (!NOT_REQUIRED.equals(currentStatus) && !REJECTED.equals(currentStatus)) {
            throw new ValidationException("Entity is already in approval flow: " + currentStatus);
        }

        String approverName = getDisplayName(submittedById);
        ApprovalRecord record = ApprovalRecord.builder()
                .entityType(entityType)
                .entityId(entityId)
                .levelNumber(0)
                .approverId(submittedById)
                .approverName(approverName)
                .status("PENDING")
                .comment("Submitted for verification")
                .build();
        approvalRecordRepository.save(record);

        setApprovalStatus(entityType, entityId, PENDING_VERIFIER, submittedById);

        return buildResponse(entityType, entityId, PENDING_VERIFIER);
    }

    @Override
    @Transactional
    public TransactionalApprovalResponse approve(String entityType, UUID entityId, UUID approverId, String comment) {
        String currentStatus = getCurrentApprovalStatus(entityType, entityId);

        String nextStatus;
        int levelNumber;

        if (PENDING_VERIFIER.equals(currentStatus)) {
            nextStatus = PENDING_AUTHORIZER;
            levelNumber = 1;
        } else if (PENDING_AUTHORIZER.equals(currentStatus)) {
            nextStatus = APPROVED;
            levelNumber = 2;
        } else {
            throw new ValidationException("Entity is not pending approval. Current status: " + currentStatus);
        }

        String approverName = getDisplayName(approverId);
        ApprovalRecord record = ApprovalRecord.builder()
                .entityType(entityType)
                .entityId(entityId)
                .levelNumber(levelNumber)
                .approverId(approverId)
                .approverName(approverName)
                .status(APPROVED)
                .comment(comment)
                .build();
        approvalRecordRepository.save(record);

        setApprovalStatus(entityType, entityId, nextStatus, null);

        return buildResponse(entityType, entityId, nextStatus);
    }

    @Override
    @Transactional
    public TransactionalApprovalResponse reject(String entityType, UUID entityId, UUID approverId, String reason) {
        String currentStatus = getCurrentApprovalStatus(entityType, entityId);
        if (!PENDING_VERIFIER.equals(currentStatus) && !PENDING_AUTHORIZER.equals(currentStatus)) {
            throw new ValidationException("Entity is not pending approval. Current status: " + currentStatus);
        }

        int levelNumber = PENDING_VERIFIER.equals(currentStatus) ? 1 : 2;
        String approverName = getDisplayName(approverId);

        ApprovalRecord record = ApprovalRecord.builder()
                .entityType(entityType)
                .entityId(entityId)
                .levelNumber(levelNumber)
                .approverId(approverId)
                .approverName(approverName)
                .status(REJECTED)
                .comment(reason)
                .build();
        approvalRecordRepository.save(record);

        setApprovalStatus(entityType, entityId, REJECTED, null);

        return buildResponse(entityType, entityId, REJECTED);
    }

    @Override
    public TransactionalApprovalResponse getHistory(String entityType, UUID entityId) {
        String currentStatus = getCurrentApprovalStatus(entityType, entityId);
        return buildResponse(entityType, entityId, currentStatus);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private String getCurrentApprovalStatus(String entityType, UUID entityId) {
        return switch (entityType.toUpperCase()) {
            case "ORDER" -> {
                Order o = orderRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", entityId));
                yield o.getApprovalStatus() != null ? o.getApprovalStatus() : NOT_REQUIRED;
            }
            case "STOCK_TRANSFER" -> {
                StockTransfer st = stockTransferRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", "id", entityId));
                yield st.getApprovalStatus() != null ? st.getApprovalStatus() : NOT_REQUIRED;
            }
            case "SUPPLIER_BILL" -> {
                SupplierBill bill = supplierBillRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("SupplierBill", "id", entityId));
                yield bill.getApprovalStatus() != null ? bill.getApprovalStatus() : NOT_REQUIRED;
            }
            case "FUNDS_TRANSFER" -> {
                FundsTransfer ft = fundsTransferRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("FundsTransfer", "id", entityId));
                yield ft.getApprovalStatus() != null ? ft.getApprovalStatus() : NOT_REQUIRED;
            }
            default -> throw new ValidationException("Unsupported entity type: " + entityType);
        };
    }

    private void setApprovalStatus(String entityType, UUID entityId, String status, UUID submittedById) {
        switch (entityType.toUpperCase()) {
            case "ORDER" -> {
                Order order = orderRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", entityId));
                order.setApprovalStatus(status);
                if (submittedById != null) order.setSubmittedById(submittedById);
                orderRepository.save(order);
            }
            case "STOCK_TRANSFER" -> {
                StockTransfer st = stockTransferRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", "id", entityId));
                st.setApprovalStatus(status);
                if (submittedById != null) st.setSubmittedById(submittedById);
                stockTransferRepository.save(st);
            }
            case "SUPPLIER_BILL" -> {
                SupplierBill bill = supplierBillRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("SupplierBill", "id", entityId));
                bill.setApprovalStatus(status);
                if (submittedById != null) bill.setSubmittedById(submittedById);
                supplierBillRepository.save(bill);
            }
            case "FUNDS_TRANSFER" -> {
                FundsTransfer ft = fundsTransferRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("FundsTransfer", "id", entityId));
                ft.setApprovalStatus(status);
                fundsTransferRepository.save(ft);
            }
            default -> throw new ValidationException("Unsupported entity type: " + entityType);
        }
    }

    private String getDisplayName(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse(userId.toString());
    }

    private TransactionalApprovalResponse buildResponse(String entityType, UUID entityId, String currentStatus) {
        List<ApprovalRecord> records = approvalRecordRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType.toUpperCase(), entityId);

        List<ApprovalRecordResponse> history = records.stream()
                .map(r -> ApprovalRecordResponse.builder()
                        .id(r.getId())
                        .levelNumber(r.getLevelNumber())
                        .approverId(r.getApproverId())
                        .approverName(r.getApproverName())
                        .status(r.getStatus())
                        .comment(r.getComment())
                        .createdAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return TransactionalApprovalResponse.builder()
                .entityId(entityId)
                .entityType(entityType)
                .approvalStatus(currentStatus)
                .history(history)
                .build();
    }
}
