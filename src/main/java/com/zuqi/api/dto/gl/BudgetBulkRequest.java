package com.zuqi.api.dto.gl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetBulkRequest {

    @NotNull @Min(2000) @Max(2100)
    private Integer budgetYear;

    @NotNull @Min(1) @Max(12)
    private Integer periodMonth;

    @NotEmpty
    @Valid
    private List<BudgetRequest> entries;
}
