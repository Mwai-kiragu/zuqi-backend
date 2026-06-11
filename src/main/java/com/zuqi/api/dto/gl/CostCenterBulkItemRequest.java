package com.zuqi.api.dto.gl;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class CostCenterBulkItemRequest {

    @NotBlank
    @Size(max = 20)
    private String code;

    @NotBlank
    @Size(max = 100)
    private String name;

    private String description;

    /** Code of the parent cost centre (resolved to UUID during import). */
    private String parentCode;
}
