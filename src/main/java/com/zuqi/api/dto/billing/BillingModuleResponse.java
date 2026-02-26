package com.zuqi.api.dto.billing;

import com.zuqi.domain.billing.BillingModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingModuleResponse {

    private UUID id;
    private String moduleKey;
    private String displayName;
    private String description;
    private boolean active;
    private int sortOrder;

    public static BillingModuleResponse fromEntity(BillingModule m) {
        return BillingModuleResponse.builder()
                .id(m.getId())
                .moduleKey(m.getModuleKey())
                .displayName(m.getDisplayName())
                .description(m.getDescription())
                .active(m.isActive())
                .sortOrder(m.getSortOrder())
                .build();
    }
}
