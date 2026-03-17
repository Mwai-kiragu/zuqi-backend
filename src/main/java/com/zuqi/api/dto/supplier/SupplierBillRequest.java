package com.zuqi.api.dto.supplier;

import com.zuqi.domain.supplier.SupplierBillType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class SupplierBillRequest {

    @NotNull(message = "Supplier is required")
    private UUID supplierId;

    private UUID purchaseOrderId;

    private String referenceNumber;

    @NotNull(message = "Bill date is required")
    private LocalDate billDate;

    private LocalDate dueDate;

    @NotNull(message = "Bill type is required")
    private SupplierBillType billType;

    private String description;

    private List<SupplierBillLineItem> items;

    @NotNull(message = "Total amount is required")
    private BigDecimal totalAmount;

    private String notes;
}
