package com.zuqi.api.dto.merchant;

import com.zuqi.domain.merchant.MerchantCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantCategoryResponse {

    private Long id;
    private String name;
    private String description;

    public static MerchantCategoryResponse fromEntity(MerchantCategory category) {
        return MerchantCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .build();
    }
}
