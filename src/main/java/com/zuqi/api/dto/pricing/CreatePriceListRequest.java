package com.zuqi.api.dto.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreatePriceListRequest {
    @NotBlank private String name;
    private String description;
    private boolean isDefault;
    private LocalDate validFrom;
    private LocalDate validTo;
    @Valid private List<PriceListItemRequest> items;
}
