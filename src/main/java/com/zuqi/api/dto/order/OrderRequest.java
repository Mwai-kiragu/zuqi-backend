package com.zuqi.api.dto.order;

import com.zuqi.domain.order.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating/updating orders.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotNull(message = "Distributor ID is required")
    private UUID distributorId;

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    private UUID salesRepId;

    private UUID warehouseId;

    @Builder.Default
    private OrderType orderType = OrderType.STANDARD;

    private BigDecimal discountAmount;

    private Integer paymentTermsDays;

    private String deliveryAddress;

    private BigDecimal deliveryLatitude;

    private BigDecimal deliveryLongitude;

    private String notes;

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;
}
