package com.zuqi.api.dto.billing;

import com.zuqi.domain.billing.BillingPackageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackageDefinitionResponse {

    private String name;
    private String displayName;
    private List<String> includedModules;

    public static List<PackageDefinitionResponse> all() {
        return Arrays.stream(BillingPackageType.values())
                .map(p -> PackageDefinitionResponse.builder()
                        .name(p.name())
                        .displayName(toDisplayName(p))
                        .includedModules(p.getIncludedModules())
                        .build())
                .collect(Collectors.toList());
    }

    private static String toDisplayName(BillingPackageType type) {
        return switch (type) {
            case FREE_TRIAL -> "Free Trial";
            case SILVER -> "Silver";
            case GOLD -> "Gold";
            case CUSTOM -> "Custom";
        };
    }
}
