package com.zuqi.ai.assistant;

import com.zuqi.domain.ai.ChatMessageType;
import com.zuqi.domain.ai.ChatRole;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.ChatMessageRepository;
import com.zuqi.repository.DistributorRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * LangChain4j {@link ChatMemoryStore} backed by PostgreSQL (ai_chat_messages table).
 *
 * Strategy
 * --------
 * • getMessages  : on the FIRST call of a new turn (after clearTurnBuffer) loads from DB and
 *                  seeds turnBuffer. On subsequent calls within the SAME turn (LC4j 0.35.0
 *                  calls this after every MessageWindowChatMemory.add() inside the agentic
 *                  tool-calling loop), returns the in-memory buffer instead of reloading from DB.
 *                  This preserves ToolExecutionRequestMessage / ToolExecutionResultMessage
 *                  entries in the context window so the model sees its own tool results and
 *                  does NOT call the same tool again — fixing the "infinite tool loop" bug.
 *
 * • updateMessages: always updates turnBuffer with the FULL message list (including tool
 *                   messages), then persists only NEW USER / ASSISTANT rows to DB using
 *                   countByConversationId as the DB watermark.
 *
 * • deleteMessages: cleans up all DB rows and clears the buffer.
 *
 * • clearTurnBuffer: call at the END of each agent turn (AssistantService.chat() finally block)
 *                    so the next turn reloads fresh history from DB.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository chatMessageRepository;
    private final DistributorRepository distributorRepository;

    /**
     * In-memory buffer per conversation that preserves ALL message types
     * (including ToolExecutionRequestMessage / ToolExecutionResultMessage)
     * for the duration of a single agent turn. Cleared by clearTurnBuffer()
     * at the end of the turn so the next turn reloads from DB.
     */
    private final ConcurrentHashMap<UUID, List<dev.langchain4j.data.message.ChatMessage>>
            turnBuffer = new ConcurrentHashMap<>();

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns the in-memory turn buffer if present (preserving tool messages across
     * LC4j's internal agentic loop steps), otherwise loads USER/ASSISTANT history from DB.
     */
    @Override
    @Transactional(readOnly = true)
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        UUID conversationId = toUuid(memoryId);
        if (conversationId == null) return Collections.emptyList();

        // If we have an active turn buffer return it — this is the key fix:
        // tool call + result messages are kept in the buffer between LC4j steps
        // so the model sees "I already called getSalesTrend and got a result"
        // and does not call it again.
        List<dev.langchain4j.data.message.ChatMessage> buffered = turnBuffer.get(conversationId);
        if (buffered != null) {
            log.debug("ChatMemoryStore.getMessages conversation={} returning turn buffer (size={})",
                    conversationId, buffered.size());
            return new ArrayList<>(buffered);
        }

        // No active turn — load history from DB (USER + ASSISTANT only)
        try {
            List<com.zuqi.domain.ai.ChatMessage> rows =
                    chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

            List<dev.langchain4j.data.message.ChatMessage> messages = rows.stream()
                    .map(this::toLC4jMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.debug("ChatMemoryStore.getMessages conversation={} loaded {} rows from DB",
                    conversationId, messages.size());
            return messages;

        } catch (Exception e) {
            log.error("ChatMemoryStore.getMessages failed for conversation={}: {}",
                    conversationId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Updates the in-memory turn buffer with the FULL message list (including tool messages),
     * then persists only NEW USER/ASSISTANT messages to DB using countByConversationId
     * as the DB watermark.
     */
    @Override
    @Transactional
    public void updateMessages(Object memoryId,
                               List<dev.langchain4j.data.message.ChatMessage> messages) {
        UUID conversationId = toUuid(memoryId);
        if (conversationId == null || messages == null || messages.isEmpty()) return;

        // Always update the in-memory buffer with the complete list (preserves tool messages)
        turnBuffer.put(conversationId, new ArrayList<>(messages));

        AssistantMemoryContext.MemoryContext ctx = AssistantMemoryContext.get();
        if (ctx == null) {
            log.warn("ChatMemoryStore.updateMessages: no context set for conversation={}",
                    conversationId);
            return;
        }

        try {
            // Count only persistable (USER + ASSISTANT with text) messages in the current list
            List<dev.langchain4j.data.message.ChatMessage> persistable = messages.stream()
                    .filter(m -> m instanceof UserMessage ||
                            (m instanceof AiMessage am && am.text() != null))
                    .collect(Collectors.toList());

            long dbCount = chatMessageRepository.countByConversationId(conversationId);
            if (persistable.size() <= dbCount) {
                log.debug("ChatMemoryStore.updateMessages conversation={} — no new persistable msgs (db={}, list={})",
                        conversationId, dbCount, persistable.size());
                return;
            }

            List<dev.langchain4j.data.message.ChatMessage> newPersistable =
                    persistable.subList((int) dbCount, persistable.size());

            Distributor distributor = distributorRepository.findById(ctx.distributorId())
                    .orElse(null);
            if (distributor == null) {
                log.error("ChatMemoryStore.updateMessages: distributor {} not found", ctx.distributorId());
                return;
            }

            int saved = 0;
            for (dev.langchain4j.data.message.ChatMessage msg : newPersistable) {
                com.zuqi.domain.ai.ChatMessage entity =
                        toDbMessage(msg, conversationId, distributor, ctx);
                if (entity != null) {
                    chatMessageRepository.save(entity);
                    saved++;
                }
            }

            log.debug("ChatMemoryStore.updateMessages conversation={} persisted {} new messages (buffer size={})",
                    conversationId, saved, messages.size());

        } catch (Exception e) {
            log.error("ChatMemoryStore.updateMessages failed for conversation={}: {}",
                    conversationId, e.getMessage(), e);
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        UUID conversationId = toUuid(memoryId);
        if (conversationId == null) return;
        chatMessageRepository.deleteByConversationId(conversationId);
        turnBuffer.remove(conversationId);
        log.debug("ChatMemoryStore.deleteMessages conversation={}", conversationId);
    }

    // ── Turn lifecycle ────────────────────────────────────────────────────────

    /**
     * Clears the in-memory turn buffer for the given conversation.
     * Call this at the END of each agent turn (in AssistantService.chat() finally block)
     * so that the next turn's first getMessages() reloads history from DB.
     */
    public void clearTurnBuffer(UUID conversationId) {
        if (conversationId == null) return;
        turnBuffer.remove(conversationId);
        log.debug("ChatMemoryStore.clearTurnBuffer conversation={}", conversationId);
    }

    // ── Converters ────────────────────────────────────────────────────────────

    /**
     * DB row → LangChain4j message.
     */
    private dev.langchain4j.data.message.ChatMessage toLC4jMessage(
            com.zuqi.domain.ai.ChatMessage m) {
        if (m.getContent() == null) return null;
        return switch (m.getRole()) {
            case USER      -> UserMessage.from(m.getContent());
            case ASSISTANT -> AiMessage.from(m.getContent());
        };
    }

    /**
     * LangChain4j message → DB row.
     * Tool messages are skipped — our schema has no TOOL role and they don't need persistence.
     */
    private com.zuqi.domain.ai.ChatMessage toDbMessage(
            dev.langchain4j.data.message.ChatMessage msg,
            UUID conversationId,
            Distributor distributor,
            AssistantMemoryContext.MemoryContext ctx) {

        ChatRole role;
        String content;

        if (msg instanceof UserMessage um) {
            role    = ChatRole.USER;
            content = um.singleText();
        } else if (msg instanceof AiMessage am) {
            role    = ChatRole.ASSISTANT;
            content = am.text();
            if (content == null) return null; // tool-call-only AiMessage, no text yet
        } else if (msg instanceof ToolExecutionResultMessage) {
            return null; // skip — ephemeral, not in our schema
        } else {
            return null; // ToolExecutionRequestMessage or future types
        }

        return com.zuqi.domain.ai.ChatMessage.builder()
                .conversationId(conversationId)
                .distributor(distributor)
                .userId(ctx.userId())
                .role(role)
                .content(content)
                .messageType(ChatMessageType.CHAT)
                .modelName(ctx.modelName())
                .build();
    }

    private UUID toUuid(Object memoryId) {
        if (memoryId instanceof UUID uuid) return uuid;
        try {
            return UUID.fromString(memoryId.toString());
        } catch (Exception e) {
            log.error("ChatMemoryStore: invalid memoryId '{}'", memoryId);
            return null;
        }
    }
}
