package com.zuqi.api.dto.pos;

import com.zuqi.domain.pos.PosPaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProcessPaymentRequest {

    @NotNull(message = "Payment method is required")
    private PosPaymentMethod paymentMethod;

    @NotNull
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String referenceNumber;

    private String notes;
}
