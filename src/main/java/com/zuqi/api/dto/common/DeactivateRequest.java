package com.zuqi.api.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for deactivating entities with a reason.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeactivateRequest {

    @NotBlank(message = "Deactivation reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
