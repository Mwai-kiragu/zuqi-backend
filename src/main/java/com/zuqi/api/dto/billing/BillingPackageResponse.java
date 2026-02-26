package com.zuqi.api.dto.billing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.domain.billing.BillingPackageDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingPackageResponse {

    private UUID id;
    private String name;
    private String displayName;
    private String description;
    private boolean isSystem;
    private List<String> modules;
    private boolean active;
    private int sortOrder;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static BillingPackageResponse fromEntity(BillingPackageDefinition pkg) {
        return BillingPackageResponse.builder()
                .id(pkg.getId())
                .name(pkg.getName())
                .displayName(pkg.getDisplayName())
                .description(pkg.getDescription())
                .isSystem(pkg.isSystem())
                .modules(parseModules(pkg.getModules()))
                .active(pkg.isActive())
                .sortOrder(pkg.getSortOrder())
                .build();
    }

    private static List<String> parseModules(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
