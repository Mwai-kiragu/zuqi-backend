package com.zuqi.api.dto.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualInvoiceRequest {

    @NotNull
    private UUID customerId;

    /** Optional: resolved from security context when not provided. */
    private UUID distributorId;

    /** Optional warehouse for stock deduction. */
    private UUID warehouseId;

    @NotNull
    @NotEmpty
    @Valid
    private List<ManualInvoiceItemRequest> items;

    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private Integer paymentTermsDays;
    private String notes;
    private String termsAndConditions;
}
