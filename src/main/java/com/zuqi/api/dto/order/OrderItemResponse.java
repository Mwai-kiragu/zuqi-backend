package com.zuqi.api.dto.order;

import com.zuqi.domain.order.OrderItem;
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
public class OrderItemResponse {

    private UUID id;
    private UUID orderId;
    private UUID productId;
    private String productSku;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountPercent;
    private BigDecimal totalAmount;
    private String promotionName;

    public static OrderItemResponse fromEntity(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .orderId(item.getOrder() != null ? item.getOrder().getId() : null)
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productSku(item.getProduct() != null ? item.getProduct().getSku() : null)
                .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .discountPercent(item.getDiscountPercent())
                .totalAmount(item.getTotalAmount())
                .promotionName(item.getPromotionName())
                .build();
    }
}
