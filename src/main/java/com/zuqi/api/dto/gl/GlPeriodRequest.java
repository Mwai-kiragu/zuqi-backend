package com.zuqi.api.dto.gl;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlPeriodRequest {

    @NotNull(message = "Period year is required")
    @Min(2000) @Max(2100)
    private Integer periodYear;

    @NotNull(message = "Period month is required")
    @Min(1) @Max(12)
    private Integer periodMonth;

    /** Days after period end before automatic lock fires. Defaults to 5. */
    @Builder.Default
    @Min(0) @Max(90)
    private int gracePeriodDays = 5;
}
