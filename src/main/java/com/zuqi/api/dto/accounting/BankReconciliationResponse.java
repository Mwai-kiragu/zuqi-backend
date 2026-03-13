package com.zuqi.api.dto.accounting;

import com.zuqi.domain.accounting.BankReconciliation;
import com.zuqi.domain.accounting.BankReconciliationStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
public class BankReconciliationResponse {
    private UUID id;
    private String accountName;
    private String accountNumber;
    private String bankName;
    private LocalDate statementDate;
    private BigDecimal statementBalance;
    private BigDecimal systemBalance;
    private BigDecimal adjustedBankBalance;
    private BigDecimal adjustedSystemBalance;
    private BigDecimal difference;
    private BankReconciliationStatus status;
    private String notes;
    private LocalDateTime reconciledAt;
    private LocalDateTime createdAt;
    private List<ReconciliationItemResponse> items;

    public static BankReconciliationResponse from(BankReconciliation r) {
        return BankReconciliationResponse.builder()
                .id(r.getId())
                .accountName(r.getAccountName())
                .accountNumber(r.getAccountNumber())
                .bankName(r.getBankName())
                .statementDate(r.getStatementDate())
                .statementBalance(r.getStatementBalance())
                .systemBalance(r.getSystemBalance())
                .adjustedBankBalance(r.getAdjustedBankBalance())
                .adjustedSystemBalance(r.getAdjustedSystemBalance())
                .difference(r.getDifference())
                .status(r.getStatus())
                .notes(r.getNotes())
                .reconciledAt(r.getReconciledAt())
                .createdAt(r.getCreatedAt())
                .items(r.getItems() != null
                        ? r.getItems().stream().map(ReconciliationItemResponse::from).collect(Collectors.toList())
                        : List.of())
                .build();
    }
}
