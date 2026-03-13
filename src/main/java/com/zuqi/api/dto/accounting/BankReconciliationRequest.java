package com.zuqi.api.dto.accounting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class BankReconciliationRequest {
    @NotBlank
    private String accountName;
    private String accountNumber;
    private String bankName;
    @NotNull
    private LocalDate statementDate;
    @NotNull
    private BigDecimal statementBalance;
    @NotNull
    private BigDecimal systemBalance;
    private String notes;
    private List<ReconciliationItemRequest> items = new ArrayList<>();
}
