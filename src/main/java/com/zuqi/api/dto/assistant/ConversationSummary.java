package com.zuqi.api.dto.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {

    private UUID conversationId;
    private long messageCount;
    private LocalDateTime lastMessageAt;
    /** Preview — first 120 chars of the last USER message */
    private String lastUserMessage;
}
