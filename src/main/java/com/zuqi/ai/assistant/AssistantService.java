package com.zuqi.ai.assistant;

import com.zuqi.domain.ai.ChatMessage;
import com.zuqi.domain.ai.ChatMessageType;
import com.zuqi.domain.ai.ChatRole;
import com.zuqi.domain.ai.ReportType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.ChatMessageRepository;
import com.zuqi.repository.DistributorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the AI assistant chat and report generation workflows.
 *
 * Memory architecture
 * -------------------
 * LangChain4j ChatMemory (via AssistantChatMemoryStore + MessageWindowChatMemory):
 *   - Stores conversation turns in PostgreSQL (ai_chat_messages table)
 *   - Loaded by LangChain4j on every agent call → Ollama receives proper alternating
 *     Human/AI messages, not a flat text dump
 *   - maxMessages = 40 (20 full turns) enforced by the window
 *
 * Redis cache ("chat-history", 30-min TTL):
 *   - Used exclusively by the history/conversations endpoints for fast read
 *   - Evicted after each turn so the API always returns fresh data
 *   - DB is the source of truth — Redis is purely a read-through cache
 *
 * Context passing:
 *   - AssistantMemoryContext (ThreadLocal) carries distributorId + userId + modelName
 *     from this service into AssistantChatMemoryStore, which needs them to populate
 *     DB rows (LangChain4j's ChatMemoryStore API only passes the memoryId)
 */
@SuppressWarnings("DataFlowIssue")
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantService {

    private static final String HISTORY_CACHE = "chat-history";

    private final AssistantAgent          assistantAgent;
    private final AssistantReportBuilder  reportBuilder;
    private final ChatMessageRepository   chatMessageRepository;
    private final DistributorRepository   distributorRepository;
    private final CacheManager            cacheManager;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String chatModelName;

    @Value("${langchain4j.ollama.report-model.model-name}")
    private String reportModelName;

    // ── Chat ─────────────────────────────────────────────────────────────────

    /**
     * Process a single chat turn.
     *
     * LangChain4j ChatMemory handles persistence of the user message and AI reply
     * via AssistantChatMemoryStore.  This method:
     *  1. Sets ThreadLocal context so the store can populate DB rows with metadata
     *  2. Passes only the distributorId-prefixed question to the agent (no manual history)
     *  3. Records durationMs on the saved assistant message
     *  4. Evicts the Redis history cache
     *
     * @param distributorId  multi-tenant scope
     * @param userId         authenticated user
     * @param conversationId session UUID (frontend generates on first message)
     * @param userText       the question
     * @return persisted ASSISTANT reply
     */
    public ChatMessage chat(UUID distributorId, UUID userId,
                            UUID conversationId, String userText) {

        loadDistributor(distributorId); // validate exists upfront

        AssistantMemoryContext.set(distributorId, userId, chatModelName);
        try {
            // Prefix every message with distributorId so the LLM always passes the correct
            // tenant to tool calls (the memory window shows previous turns — without this
            // prefix the model might re-use a distributorId from an earlier message)
            String contextualMessage = "DISTRIBUTOR_ID: " + distributorId + "\n\n" + userText;

            long t0 = System.currentTimeMillis();
            String reply;
            try {
                reply = assistantAgent.chat(conversationId, contextualMessage);
            } catch (Exception e) {
                log.error("AssistantAgent failed for conversation={}: {}",
                        conversationId, e.getMessage(), e);
                reply = "I'm sorry, I encountered an error while processing your request. " +
                        "Please try again or contact support if the issue persists.";
                // Still save an error assistant message so the conversation is coherent
                saveErrorAssistantMessage(conversationId, distributorId, userId, reply);
                evictHistoryCache(conversationId);
                return chatMessageRepository
                        .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 1))
                        .getContent().get(0);
            }
            long durationMs = System.currentTimeMillis() - t0;

            // Update durationMs on the assistant message the store just persisted
            updateLatestAssistantDuration(conversationId, durationMs);
            evictHistoryCache(conversationId);

            // Return the freshly-saved assistant message
            return chatMessageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 1))
                    .getContent().get(0);

        } finally {
            AssistantMemoryContext.clear();
        }
    }

    // ── Report ────────────────────────────────────────────────────────────────

    /**
     * Generate a structured report and persist it as a REPORT-type ASSISTANT message.
     *
     * Reports are NOT routed through the LangChain4j agent — data is gathered directly
     * from tools by AssistantReportBuilder, then sent to AssistantReportAiService.
     * We manually persist both the USER request and the ASSISTANT report to DB so
     * the conversation thread stays intact.
     */
    @Transactional
    public ChatMessage generateReport(UUID distributorId, UUID userId,
                                      UUID conversationId, ReportType reportType,
                                      Map<String, Object> params) {

        Distributor distributor = loadDistributor(distributorId);

        // Persist the user's report request turn
        String requestText = "Generate a " + reportType.name().replace("_", " ").toLowerCase() + " report";
        ChatMessage userMsg = ChatMessage.builder()
                .conversationId(conversationId)
                .distributor(distributor)
                .userId(userId)
                .role(ChatRole.USER)
                .content(requestText)
                .messageType(ChatMessageType.REPORT)
                .reportType(reportType)
                .reportParams(params)
                .modelName(reportModelName)
                .build();
        chatMessageRepository.save(userMsg);

        // Build the report via AssistantReportBuilder → AssistantReportAiService
        long t0 = System.currentTimeMillis();
        String reportContent;
        try {
            reportContent = reportBuilder.build(distributorId, reportType, params);
        } catch (Exception e) {
            log.error("Report generation failed for type={} distributor={}: {}",
                    reportType, distributorId, e.getMessage(), e);
            reportContent = "# Report Generation Failed\n\nUnable to generate the " +
                    reportType.name() + " report at this time. Please try again later.";
        }
        long durationMs = System.currentTimeMillis() - t0;

        // Persist the report as ASSISTANT turn
        ChatMessage reportMsg = ChatMessage.builder()
                .conversationId(conversationId)
                .distributor(distributor)
                .userId(userId)
                .role(ChatRole.ASSISTANT)
                .content(reportContent)
                .messageType(ChatMessageType.REPORT)
                .reportType(reportType)
                .reportParams(params)
                .modelName(reportModelName)
                .durationMs(durationMs)
                .build();

        log.info("Generated {} report for distributor={} in {}ms",
                reportType, distributorId, durationMs);
        ChatMessage saved = chatMessageRepository.save(reportMsg);
        evictHistoryCache(conversationId);
        return saved;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Set durationMs on the most recent ASSISTANT message in the conversation.
     * Called after the agent returns so we have the wall-clock time for the full turn.
     */
    private void updateLatestAssistantDuration(UUID conversationId, long durationMs) {
        try {
            List<ChatMessage> recent = chatMessageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 5))
                    .getContent();
            recent.stream()
                    .filter(m -> m.getRole() == ChatRole.ASSISTANT && m.getDurationMs() == null)
                    .findFirst()
                    .ifPresent(m -> {
                        m.setDurationMs(durationMs);
                        chatMessageRepository.save(m);
                    });
        } catch (Exception e) {
            log.warn("Could not update durationMs for conversation={}: {}", conversationId, e.getMessage());
        }
    }

    /**
     * Save a fallback error message as an ASSISTANT turn when the agent itself throws.
     * Ensures the conversation thread is always complete (user turn → assistant turn).
     */
    private void saveErrorAssistantMessage(UUID conversationId, UUID distributorId,
                                           UUID userId, String errorReply) {
        try {
            Distributor distributor = loadDistributor(distributorId);
            ChatMessage err = ChatMessage.builder()
                    .conversationId(conversationId)
                    .distributor(distributor)
                    .userId(userId)
                    .role(ChatRole.ASSISTANT)
                    .content(errorReply)
                    .messageType(ChatMessageType.CHAT)
                    .modelName(chatModelName)
                    .build();
            chatMessageRepository.save(err);
        } catch (Exception ex) {
            log.error("Could not save error assistant message for conversation={}", conversationId, ex);
        }
    }

    /**
     * Evict the Redis history cache for a conversation.
     * Uses CacheManager directly to avoid Spring AOP self-invocation limitations.
     */
    private void evictHistoryCache(UUID conversationId) {
        Cache cache = cacheManager.getCache(HISTORY_CACHE);
        if (cache != null) {
            cache.evict(conversationId);
        }
    }

    private Distributor loadDistributor(UUID id) {
        return distributorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Distributor not found: " + id));
    }
}
