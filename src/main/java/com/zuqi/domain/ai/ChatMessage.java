package com.zuqi.domain.ai;

import com.zuqi.domain.distributor.Distributor;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted AI assistant conversation turn.
 *
 * Each row is one message — either a USER question or an ASSISTANT reply.
 * Rows with message_type = REPORT contain generated markdown reports in the content field.
 * Rows are grouped into sessions by conversation_id.
 */
@Entity
@Table(
    name = "ai_chat_messages",
    indexes = {
        @Index(name = "idx_chat_messages_conversation",     columnList = "conversation_id, created_at"),
        @Index(name = "idx_chat_messages_distributor_user", columnList = "distributor_id, user_id, created_at"),
        @Index(name = "idx_chat_messages_distributor_conv", columnList = "distributor_id, conversation_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "distributor_id", nullable = false)
    private Distributor distributor;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    private ChatMessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", length = 50)
    private ReportType reportType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_params", columnDefinition = "jsonb")
    private Map<String, Object> reportParams;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (messageType == null) {
            messageType = ChatMessageType.CHAT;
        }
    }
}
