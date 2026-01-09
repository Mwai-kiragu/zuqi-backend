package com.zuqi.api.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentOrderResponse {
    private UUID id;
    private String orderNumber;
    private String merchantName;
    private String status;
    private String paymentStatus;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
}
