package com.zuqi.api.dto.pos;

import com.zuqi.domain.pos.PosPaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PosSalePaymentResponse {

    private UUID id;
    private PosPaymentMethod paymentMethod;
    private BigDecimal amount;
    private String referenceNumber;
    private String notes;
    private LocalDateTime createdAt;
}
