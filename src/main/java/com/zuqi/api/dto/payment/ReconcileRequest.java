package com.zuqi.api.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for reconciling payments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconcileRequest {

    private String notes;
}
