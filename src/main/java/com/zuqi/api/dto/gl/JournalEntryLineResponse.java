package com.zuqi.api.dto.gl;

import com.zuqi.domain.gl.JournalEntryLine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryLineResponse {

    private UUID id;
    private int lineNumber;
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private UUID costCenterId;
    private String costCenterCode;
    private String costCenterName;
    private String description;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String reference;
    private LocalDateTime createdAt;

    public static JournalEntryLineResponse fromEntity(JournalEntryLine l) {
        return JournalEntryLineResponse.builder()
                .id(l.getId())
                .lineNumber(l.getLineNumber())
                .accountId(l.getAccount() != null ? l.getAccount().getId() : null)
                .accountCode(l.getAccount() != null ? l.getAccount().getAccountCode() : null)
                .accountName(l.getAccount() != null ? l.getAccount().getAccountName() : null)
                .costCenterId(l.getCostCenter() != null ? l.getCostCenter().getId() : null)
                .costCenterCode(l.getCostCenter() != null ? l.getCostCenter().getCode() : null)
                .costCenterName(l.getCostCenter() != null ? l.getCostCenter().getName() : null)
                .description(l.getDescription())
                .debitAmount(l.getDebitAmount())
                .creditAmount(l.getCreditAmount())
                .reference(l.getReference())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
