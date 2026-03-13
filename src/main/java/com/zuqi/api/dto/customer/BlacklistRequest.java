package com.zuqi.api.dto.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BlacklistRequest {

    @NotBlank(message = "Reason is required")
    private String reason;
}
