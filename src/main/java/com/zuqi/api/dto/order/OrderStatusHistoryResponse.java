package com.zuqi.api.dto.order;

import com.zuqi.domain.order.OrderStatus;
import com.zuqi.domain.order.OrderStatusHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusHistoryResponse {

    private UUID id;
    private UUID orderId;
    private OrderStatus status;
    private String notes;
    private UUID changedById;
    private String changedByName;
    private LocalDateTime createdAt;

    public static OrderStatusHistoryResponse fromEntity(OrderStatusHistory history) {
        return OrderStatusHistoryResponse.builder()
                .id(history.getId())
                .orderId(history.getOrder() != null ? history.getOrder().getId() : null)
                .status(history.getStatus())
                .notes(history.getNotes())
                .changedById(history.getChangedBy() != null ? history.getChangedBy().getId() : null)
                .changedByName(history.getChangedBy() != null ? history.getChangedBy().getFullName() : null)
                .createdAt(history.getCreatedAt())
                .build();
    }
}
