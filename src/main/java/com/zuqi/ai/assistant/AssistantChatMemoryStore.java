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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LangChain4j {@link ChatMemoryStore} backed by PostgreSQL (ai_chat_messages table).
 *
 * Strategy
 * --------
 * • getMessages  : loads the conversation from DB, maps rows → LangChain4j message objects.
 *                  This gives the LLM proper alternating Human/AI messages (not just a
 *                  flat text dump) so Ollama understands conversation structure natively.
 *
 * • updateMessages: called by LangChain4j after each agent step (after user msg is added,
 *                   after each tool result, after the final AI reply).
 *                   We compare the new list length against the current DB count to identify
 *                   and persist only the newly-added messages.
 *                   distributor + userId context is supplied via AssistantMemoryContext (ThreadLocal).
 *
 * • deleteMessages: cleans up all DB rows for a conversation (used by the DELETE endpoint).
 *
 * Tool messages (ToolExecutionRequestMessage, ToolExecutionResultMessage) are skipped when
 * persisting because our schema has only USER and ASSISTANT roles.  They are reconstructed
 * naturally on every turn since the LLM re-calls tools when needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository chatMessageRepository;
    private final DistributorRepository distributorRepository;

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Load all persisted messages for the given conversationId and convert them
     * to LangChain4j message objects for injection into the ChatMemory window.
     */
    @Override
    @Transactional(readOnly = true)
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        UUID conversationId = toUuid(memoryId);
        if (conversationId == null) return Collections.emptyList();

        try {
            List<com.zuqi.domain.ai.ChatMessage> rows =
                    chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

            List<dev.langchain4j.data.message.ChatMessage> messages = rows.stream()
                    .map(this::toLC4jMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.debug("ChatMemoryStore.getMessages conversation={} rows={} lc4j={}",
                    conversationId, rows.size(), messages.size());
            return messages;

        } catch (Exception e) {
            log.error("ChatMemoryStore.getMessages failed for conversation={}: {}",
                    conversationId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Persist any messages in {@code messages} that are not yet in the DB.
     *
     * LangChain4j calls this after every agent step, passing the FULL current
     * window.  We derive "new" messages by comparing the list size against the
     * DB row count for this conversation, then save the tail.
     *
     * Context (distributor, userId, modelName) comes from AssistantMemoryContext
     * which AssistantService sets on the thread before calling the agent.
     */
    @Override
    @Transactional
    public void updateMessages(Object memoryId,
                               List<dev.langchain4j.data.message.ChatMessage> messages) {
        UUID conversationId = toUuid(memoryId);
        if (conversationId == null || messages == null || messages.isEmpty()) return;

        AssistantMemoryContext.MemoryContext ctx = AssistantMemoryContext.get();
        if (ctx == null) {
            log.warn("ChatMemoryStore.updateMessages: no context set for conversation={}",
                    conversationId);
            return;
        }

        try {
            long dbCount = chatMessageRepository.countByConversationId(conversationId);
            if (messages.size() <= dbCount) {
                log.debug("ChatMemoryStore.updateMessages conversation={} — no new messages (db={}, list={})",
                        conversationId, dbCount, messages.size());
                return;
            }

            // Only new messages not yet persisted
            List<dev.langchain4j.data.message.ChatMessage> newMessages =
                    messages.subList((int) dbCount, messages.size());

            Distributor distributor = distributorRepository.findById(ctx.distributorId())
                    .orElse(null);
            if (distributor == null) {
                log.error("ChatMemoryStore.updateMessages: distributor {} not found", ctx.distributorId());
                return;
            }

            int saved = 0;
            for (dev.langchain4j.data.message.ChatMessage msg : newMessages) {
                com.zuqi.domain.ai.ChatMessage entity =
                        toDbMessage(msg, conversationId, distributor, ctx);
                if (entity != null) {
                    chatMessageRepository.save(entity);
                    saved++;
                }
            }

            log.debug("ChatMemoryStore.updateMessages conversation={} saved {} of {} new messages",
                    conversationId, saved, newMessages.size());

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
        log.debug("ChatMemoryStore.deleteMessages conversation={}", conversationId);
    }

    // ── Converters ────────────────────────────────────────────────────────────

    /**
     * DB row → LangChain4j message.
     * REPORT-type rows are treated as ASSISTANT chat messages (full content included
     * here because the ChatMemory window is controlled by MessageWindowChatMemory's
     * maxMessages limit, not by content size).
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
     * Tool messages (ToolExecutionRequestMessage, ToolExecutionResultMessage) are
     * skipped — our schema has no TOOL role and they don't need long-term persistence.
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
            if (content == null) return null; // AiMessage with only tool-call requests, no text
        } else if (msg instanceof ToolExecutionResultMessage) {
            return null; // skip — not user-facing, not in our schema
        } else {
            // ToolExecutionRequestMessage or future types
            return null;
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
