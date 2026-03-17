package com.zuqi.api.dto.ft;

import com.zuqi.domain.ft.FundsTransfer;
import com.zuqi.domain.ft.FundsTransferStatus;
import com.zuqi.domain.ft.FundsTransferType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class FundsTransferResponse {

    private UUID id;
    private UUID distributorId;
    private String referenceNumber;
    private FundsTransferType transferType;
    private String debitAccountNumber;
    private String debitBankName;
    private String creditAccountNumber;
    private String creditBankName;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String paymentDetails;
    private FundsTransferStatus status;
    private int currentApprovalLevel;
    private int requiredApprovalLevels;
    private UUID amountRangeId;
    private UUID initiatorId;
    private String initiatorName;
    private String referenceType;
    private UUID referenceId;
    private String referenceSummary;   // e.g. "Expense: Office Rent | KES 50,000"
    private LocalDateTime disbursedAt;
    private String rejectedReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Supplier payment linkage
    private UUID supplierId;
    private String supplierName;
    private UUID supplierBillId;
    private String billNumber;

    // Approval workflow detail
    private List<FtApprovalLevelDto> approvalLevels;     // configured approvers per level
    private List<FtApprovalDecisionDto> approvalHistory; // recorded decisions

    public static FundsTransferResponse from(FundsTransfer ft) {
        return FundsTransferResponse.builder()
                .id(ft.getId())
                .distributorId(ft.getDistributorId())
                .referenceNumber(ft.getReferenceNumber())
                .transferType(ft.getTransferType())
                .debitAccountNumber(ft.getDebitAccountNumber())
                .debitBankName(ft.getDebitBankName())
                .creditAccountNumber(ft.getCreditAccountNumber())
                .creditBankName(ft.getCreditBankName())
                .amount(ft.getAmount())
                .currency(ft.getCurrency())
                .description(ft.getDescription())
                .paymentDetails(ft.getPaymentDetails())
                .status(ft.getStatus())
                .currentApprovalLevel(ft.getCurrentApprovalLevel())
                .requiredApprovalLevels(ft.getRequiredApprovalLevels())
                .amountRangeId(ft.getAmountRangeId())
                .initiatorId(ft.getInitiatorId())
                .referenceType(ft.getReferenceType())
                .referenceId(ft.getReferenceId())
                .disbursedAt(ft.getDisbursedAt())
                .rejectedReason(ft.getRejectedReason())
                .supplierId(ft.getSupplier() != null ? ft.getSupplier().getId() : null)
                .supplierName(ft.getSupplier() != null ? ft.getSupplier().getName() : null)
                .supplierBillId(ft.getSupplierBill() != null ? ft.getSupplierBill().getId() : null)
                .billNumber(ft.getSupplierBill() != null ? ft.getSupplierBill().getBillNumber() : null)
                .createdAt(ft.getCreatedAt())
                .updatedAt(ft.getUpdatedAt())
                .build();
    }
}
