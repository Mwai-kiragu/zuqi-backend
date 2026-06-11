package com.zuqi.api.dto.pos;

import com.zuqi.domain.pos.PosSaleStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PosSaleResponse {

    private UUID id;
    private UUID branchId;
    private String branchName;
    private UUID shiftId;
    private UUID cashierId;
    private String cashierName;
    private String receiptNumber;
    private PosSaleStatus status;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal changeGiven;
    private UUID customerId;
    private String customerBusinessName;
    private String customerName;
    private String customerPhone;
    private String notes;
    private List<PosSaleItemResponse> items;
    private List<PosSalePaymentResponse> payments;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Set when a refund request is routed for approval instead of executed immediately. */
    private UUID pendingApprovalId;
    private String pendingApprovalMessage;
}
