package com.zuqi.api.dto.payment;

import com.zuqi.domain.payment.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodResponse {

    private Long id;
    private String name;
    private String code;
    private String description;
    private boolean active;

    public static PaymentMethodResponse fromEntity(PaymentMethod method) {
        return PaymentMethodResponse.builder()
                .id(method.getId())
                .name(method.getName())
                .code(method.getCode())
                .description(method.getDescription())
                .active(method.isActive())
                .build();
    }
}
