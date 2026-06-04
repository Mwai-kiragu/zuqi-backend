package com.zuqi.api.dto.gl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zuqi.domain.gl.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlAccountResponse {

    private UUID id;
    private UUID distributorId;
    private String accountCode;
    private String accountName;
    private AccountType accountType;
    private AccountSubType accountSubType;
    private NormalBalance normalBalance;
    private UUID parentId;
    private int level;
    @JsonProperty("isPostingAccount")
    private boolean isPostingAccount;
    @JsonProperty("isSystemAccount")
    private boolean isSystemAccount;
    private SystemAccountType systemAccountType;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GlAccountResponse fromEntity(GlAccount a) {
        return GlAccountResponse.builder()
                .id(a.getId())
                .distributorId(a.getDistributorId())
                .accountCode(a.getAccountCode())
                .accountName(a.getAccountName())
                .accountType(a.getAccountType())
                .accountSubType(a.getAccountSubType())
                .normalBalance(a.getNormalBalance())
                .parentId(a.getParentId())
                .level(a.getLevel())
                .isPostingAccount(a.isPostingAccount())
                .isSystemAccount(a.isSystemAccount())
                .systemAccountType(a.getSystemAccountType())
                .description(a.getDescription())
                .active(a.isActive())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
