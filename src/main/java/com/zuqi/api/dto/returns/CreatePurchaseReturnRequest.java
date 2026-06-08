package com.zuqi.api.dto.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class CreatePurchaseReturnRequest {
    @NotNull
    private UUID supplierId;
    private UUID supplierBillId;
    private UUID grnId;
    private String reason;
    @NotEmpty @Valid
    private List<LineItem> items;

    @Data
    public static class LineItem {
        @NotNull
        private UUID productId;
        @NotNull
        private BigDecimal quantity;
        @NotNull
        private BigDecimal unitPrice;
    }
}
