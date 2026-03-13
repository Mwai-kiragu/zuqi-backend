package com.zuqi.api.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ChatRequest {

    private UUID distributorId;

    @NotNull(message = "conversationId is required")
    private UUID conversationId;

    @NotBlank(message = "message is required")
    private String message;
}
