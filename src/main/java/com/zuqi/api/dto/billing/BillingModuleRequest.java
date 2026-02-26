package com.zuqi.api.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BillingModuleRequest {

    @NotBlank
    @Pattern(regexp = "^[a-z0-9_]+$", message = "moduleKey must be lowercase letters, digits or underscores")
    private String moduleKey;

    @NotBlank
    private String displayName;

    private String description;

    private Integer sortOrder;
}
