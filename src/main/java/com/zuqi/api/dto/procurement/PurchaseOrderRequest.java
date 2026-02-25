package com.zuqi.api.dto.procurement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class PurchaseOrderRequest {

    @NotNull(message = "Supplier is required")
    private UUID supplierId;

    private UUID distributorId;

    private UUID purchaseRequisitionId;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<ProcurementItemDto> items;

    private String deliveryAddress;

    private Integer paymentTermsDays;

    private LocalDate expectedDeliveryDate;

    private String notes;
}
