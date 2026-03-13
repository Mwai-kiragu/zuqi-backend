package com.zuqi.api.dto.customer;

import com.zuqi.domain.customer.CustomerCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCategoryResponse {

    private Long id;
    private String name;
    private String description;

    public static CustomerCategoryResponse fromEntity(CustomerCategory category) {
        return CustomerCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }
}
