package com.zuqi.api.dto.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.domain.billing.BillingPackageType;
import com.zuqi.domain.billing.DistributorSubscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionResponse {

    private UUID id;
    private UUID distributorId;
    private String distributorName;
    private BillingPackageType packageType;
    private List<String> enabledModules;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean active;
    private String notes;
    private LocalDateTime createdAt;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static SubscriptionResponse fromEntity(DistributorSubscription sub) {
        List<String> modules;
        String storedModules = sub.getCustomModules();
        if (storedModules != null && !storedModules.isBlank()) {
            modules = parseCustomModules(storedModules);
        } else {
            modules = sub.getPackageType().getIncludedModules();
        }

        return SubscriptionResponse.builder()
                .id(sub.getId())
                .distributorId(sub.getDistributor().getId())
                .distributorName(sub.getDistributor().getName())
                .packageType(sub.getPackageType())
                .enabledModules(modules)
                .startDate(sub.getStartDate())
                .endDate(sub.getEndDate())
                .active(sub.isActive())
                .notes(sub.getNotes())
                .createdAt(sub.getCreatedAt())
                .build();
    }

    /** Build a default FREE_TRIAL response for distributors that have no subscription yet. */
    public static SubscriptionResponse defaultFreeTrial(UUID distributorId, String distributorName) {
        return SubscriptionResponse.builder()
                .distributorId(distributorId)
                .distributorName(distributorName)
                .packageType(BillingPackageType.FREE_TRIAL)
                .enabledModules(BillingPackageType.FREE_TRIAL.getIncludedModules())
                .startDate(LocalDate.now())
                .active(true)
                .build();
    }

    private static List<String> parseCustomModules(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
