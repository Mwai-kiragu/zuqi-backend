package com.zuqi.api.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Top performing merchant for dashboard display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopMerchantResponse {
    private UUID id;
    private String businessName;
    private String city;
    private Long totalOrders;
    private BigDecimal totalSpent;
}
