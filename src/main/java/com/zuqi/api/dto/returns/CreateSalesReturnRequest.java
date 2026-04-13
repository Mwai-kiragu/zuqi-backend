package com.zuqi.api.dto.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateSalesReturnRequest {
    private UUID orderId;
    private UUID invoiceId;
    private UUID customerId;
    private String reason;
    private String refundMethod;
    @NotEmpty @Valid
    private List<SalesReturnLineItem> items;
}
