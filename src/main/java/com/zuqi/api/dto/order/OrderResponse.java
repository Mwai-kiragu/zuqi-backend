package com.zuqi.api.dto.order;

import com.zuqi.domain.order.Order;
import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.order.OrderType;
import com.zuqi.domain.order.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for orders.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID id;
    private String orderNumber;
    private UUID distributorId;
    private UUID merchantId;
    private String merchantName;
    private UUID salesRepId;
    private String salesRepName;
    private UUID warehouseId;
    private String warehouseName;
    private OrderStatus status;
    private OrderType orderType;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private PaymentStatus paymentStatus;
    private Integer paymentTermsDays;
    private LocalDate paymentDueDate;
    private String deliveryAddress;
    private BigDecimal deliveryLatitude;
    private BigDecimal deliveryLongitude;
    private String notes;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderResponse fromEntity(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .distributorId(order.getDistributor() != null ? order.getDistributor().getId() : null)
                .merchantId(order.getMerchant() != null ? order.getMerchant().getId() : null)
                .merchantName(order.getMerchant() != null ? order.getMerchant().getBusinessName() : null)
                .salesRepId(order.getSalesRep() != null ? order.getSalesRep().getId() : null)
                .salesRepName(order.getSalesRep() != null ? order.getSalesRep().getFullName() : null)
                .warehouseId(order.getWarehouse() != null ? order.getWarehouse().getId() : null)
                .warehouseName(order.getWarehouse() != null ? order.getWarehouse().getName() : null)
                .status(order.getStatus())
                .orderType(order.getOrderType())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .paidAmount(order.getPaidAmount())
                .paymentStatus(order.getPaymentStatus())
                .paymentTermsDays(order.getPaymentTermsDays())
                .paymentDueDate(order.getPaymentDueDate())
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryLatitude(order.getDeliveryLatitude())
                .deliveryLongitude(order.getDeliveryLongitude())
                .notes(order.getNotes())
                .items(order.getItems() != null
                        ? order.getItems().stream().map(OrderItemResponse::fromEntity).toList()
                        : null)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
