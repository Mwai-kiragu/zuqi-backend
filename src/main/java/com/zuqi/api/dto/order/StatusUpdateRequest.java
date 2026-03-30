package com.zuqi.api.dto.order;

import com.zuqi.domain.order.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdateRequest {

    @NotNull(message = "Status is required")
    private OrderStatus status;

    private String notes;

    /** Optional: assign a driver when transitioning to OUT_FOR_DELIVERY */
    private UUID driverId;
}
