package com.zuqi.api.dto.crm;

import com.zuqi.domain.crm.InteractionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CustomerInteractionRequest {

    @NotNull
    private UUID customerId;

    @NotNull
    private InteractionType interactionType;

    @NotBlank
    private String subject;

    private String notes;

    private String outcome;

    private LocalDate followUpDate;

    private Boolean followUpDone;
}
