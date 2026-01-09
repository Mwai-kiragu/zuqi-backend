package com.zuqi.api.dto.product;

import jakarta.validation.constraints.NotBlank;
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
public class ProductCategoryRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    private String description;

    private Long parentId;

    @NotNull(message = "Distributor ID is required")
    private UUID distributorId;
}
