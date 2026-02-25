package com.zuqi.api.dto.gl;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectJournalRequest {

    @NotBlank(message = "Rejection reason is required")
    private String reason;
}
