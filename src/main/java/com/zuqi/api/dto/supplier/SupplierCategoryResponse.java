package com.zuqi.api.dto.supplier;

import com.zuqi.domain.supplier.SupplierCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierCategoryResponse {

    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;

    public static SupplierCategoryResponse fromEntity(SupplierCategory c) {
        return SupplierCategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
