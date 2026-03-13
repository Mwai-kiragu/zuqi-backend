package com.zuqi.api.dto.billing;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zuqi.domain.billing.BillingPackageType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class AssignSubscriptionRequest {

    @NotNull
    private UUID distributorId;

    @NotNull
    private BillingPackageType packageType;

    /** Only required when packageType == CUSTOM */
    private List<String> customModules;

    /** Duration in days from today. NULL = unlimited. */
    private Integer durationDays;

    /** Explicit start date (overrides today if provided). */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** Explicit end date (overrides durationDays if provided). */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String notes;
}
