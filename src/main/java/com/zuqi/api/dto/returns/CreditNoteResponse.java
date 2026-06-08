package com.zuqi.api.dto.returns;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CreditNoteResponse {

    private UUID id;
    private String creditNoteNumber;
    private UUID distributorId;
    private UUID customerId;
    private String customerName;
    private UUID salesReturnId;
    private String salesReturnNumber;
    private UUID sourceInvoiceId;
    private String sourceInvoiceNumber;
    private BigDecimal amount;
    private BigDecimal remainingAmount;
    private String status;
    private String notes;
    private LocalDateTime expiresAt;
    private List<ApplicationResponse> applications;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class ApplicationResponse {
        private UUID id;
        private UUID invoiceId;
        private String invoiceNumber;
        private BigDecimal amountApplied;
        private LocalDateTime appliedAt;
    }
}
