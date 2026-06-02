package com.zuqi.api.dto.ft;

import com.zuqi.domain.ft.FundsTransferType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class FundsTransferRequest {

    @NotNull(message = "Transfer type is required")
    private FundsTransferType transferType;

    /** BANK_TRANSFER (default) | CHEQUE | MPESA */
    private String paymentMode;

    private String debitAccountNumber;
    private String debitBankName;

    private String creditAccountNumber;

    private String creditBankName;

    private String chequeNumber;
    private LocalDate chequeDate;
    private String chequeImageUrl;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String currency = "KES";
    private String description;
    private String paymentDetails;

    /** Optional: 'EXPENSE' or 'PURCHASE_ORDER' */
    private String referenceType;
    private UUID referenceId;

    /** Optional: link to a supplier for SUPPLIER_PAYMENT transfers */
    private UUID supplierId;

    /** Optional: link to a specific supplier bill */
    private UUID supplierBillId;
}
