package com.zuqi.api.dto.gl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryRequest {

    @NotNull(message = "Entry date is required")
    private LocalDate entryDate;

    @NotBlank(message = "Description is required")
    private String description;

    private String reference;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<JournalEntryLineRequest> lines;
}
