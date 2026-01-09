package com.zuqi.api.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class PaymentRequest {

    private UUID orderId;

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotNull(message = "Distributor ID is required")
    private UUID distributorId;

    private Long paymentMethodId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @Builder.Default
    private String currency = "KES";

    private String externalReference;

    private String notes;
}
