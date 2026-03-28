package com.zuqi.api.dto.crm;

import com.zuqi.domain.crm.CustomerInteraction;
import com.zuqi.domain.crm.InteractionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CustomerInteractionResponse {

    private UUID id;
    private UUID customerId;
    private String customerName;
    private UUID distributorId;
    private InteractionType interactionType;
    private String subject;
    private String notes;
    private String outcome;
    private LocalDate followUpDate;
    private boolean followUpDone;
    private UUID createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CustomerInteractionResponse from(CustomerInteraction ci) {
        return CustomerInteractionResponse.builder()
                .id(ci.getId())
                .customerId(ci.getCustomer().getId())
                .customerName(ci.getCustomer().getBusinessName())
                .distributorId(ci.getDistributorId())
                .interactionType(ci.getInteractionType())
                .subject(ci.getSubject())
                .notes(ci.getNotes())
                .outcome(ci.getOutcome())
                .followUpDate(ci.getFollowUpDate())
                .followUpDone(ci.isFollowUpDone())
                .createdById(ci.getCreatedBy() != null ? ci.getCreatedBy().getId() : null)
                .createdByName(ci.getCreatedBy() != null ? ci.getCreatedBy().getFirstName() + " " + ci.getCreatedBy().getLastName() : null)
                .createdAt(ci.getCreatedAt())
                .updatedAt(ci.getUpdatedAt())
                .build();
    }
}
