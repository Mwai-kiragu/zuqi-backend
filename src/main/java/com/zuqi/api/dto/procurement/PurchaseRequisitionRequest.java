package com.zuqi.api.dto.procurement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class PurchaseRequisitionRequest {

    private UUID distributorId;

    private String description;

    private String justification;

    private LocalDate expectedDeliveryDate;

    private UUID preferredSupplierId;

    private String preferredSupplierName;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<ProcurementItemDto> items;
}
