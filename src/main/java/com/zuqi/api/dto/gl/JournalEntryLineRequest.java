package com.zuqi.api.dto.gl;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryLineRequest {

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    private UUID costCenterId;

    private String description;

    private BigDecimal debitAmount;

    private BigDecimal creditAmount;

    private String reference;
}
