package com.zuqi.api.dto.gl;

import com.zuqi.domain.gl.JournalSourceModule;
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
import java.util.UUID;

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

    /** Set by system auto-posting; null = MANUAL entry from UI */
    private JournalSourceModule sourceModule;

    /** Source document UUID (invoice id, sale id, etc.) for drill-through */
    private UUID sourceDocumentId;

    @NotEmpty(message = "At least one line is required")
    @Valid
    private List<JournalEntryLineRequest> lines;
}
