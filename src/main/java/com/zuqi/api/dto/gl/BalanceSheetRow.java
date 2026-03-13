package com.zuqi.api.dto.gl;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class BalanceSheetRow {
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private BigDecimal balance;
}
