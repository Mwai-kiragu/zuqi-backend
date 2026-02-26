package com.zuqi.api.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class BillingPackageRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "name must be uppercase letters, digits or underscores")
    private String name;

    @NotBlank
    private String displayName;

    private String description;

    @NotNull
    private List<String> modules;

    private Integer sortOrder;
}
