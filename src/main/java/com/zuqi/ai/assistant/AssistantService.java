package com.zuqi.ai.assistant;

import com.zuqi.domain.ai.ChatMessage;
import com.zuqi.domain.ai.ChatMessageType;
import com.zuqi.domain.ai.ChatRole;
import com.zuqi.domain.ai.ReportType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.ChatMessageRepository;
import com.zuqi.repository.DistributorRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the AI assistant chat and report generation workflows.
 *
 * Chat strategy (primary + fallback)
 * ------------------------------------
 * PRIMARY — Direct context injection (no LLM tool calling):
 *   AssistantContextFetcher calls all 9 DB tools directly in Java,
 *   injects the results as a data block into the prompt, then calls
 *   the LLM for plain text generation (like Keza). Works with any
 *   model including gpt-oss which has broken tool-calling in Ollama.
 *
 * FALLBACK — LangChain4j AiServices tool calling (AssistantAgent):
 *   Used if the direct approach throws. Requires a model that supports
 *   function/tool calling (e.g. qwen2.5:7b, llama3-groq-tool-use).
 */
@SuppressWarnings("DataFlowIssue")
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantService {

    private static final String HISTORY_CACHE = "chat-history";

    private static final String SYSTEM_PROMPT = """
            You are an intelligent business assistant for Zuqi, a field sales and supply chain \
            platform operating in Kenya. You help distributors, sales reps, and managers \
            understand their business data and make better decisions.

            The user's live business data is provided below each question. \
            Use ONLY the provided data to answer. Format monetary values in KES with comma \
            separators (e.g. KES 1,250,000). Be concise — keep answers under 300 words \
            unless a detailed breakdown is requested. \
            Do not answer questions unrelated to Zuqi business operations.
            """;

    private final AssistantAgent            assistantAgent;
    private final AssistantContextFetcher   contextFetcher;
    private final AssistantReportBuilder    reportBuilder;
    private final ChatMessageRepository     chatMessageRepository;
    private final DistributorRepository     distributorRepository;
    private final CacheManager              cacheManager;
    private final ChatLanguageModel         chatLanguageModel;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String chatModelName;

    @Value("${langchain4j.ollama.report-model.model-name}")
    private String reportModelName;

    // ── Chat ─────────────────────────────────────────────────────────────────

    /**
     * Process a single chat turn.
     *
     * Strategy:
     *  1. Fetch all business data directly from DB via AssistantContextFetcher
     *  2. Build conversation history + data context as plain ChatMessage list
     *  3. Call LLM directly (no tool calling) — works with gpt-oss and any model
     *  4. If direct call fails, fallback to AssistantAgent (LangChain4j tool calling)
     *  5. Persist both user message and AI reply to ai_chat_messages
     */
    public ChatMessage chat(UUID distributorId, UUID userId,
                            UUID conversationId, String userText) {

        Distributor distributor = loadDistributor(distributorId);
        AssistantMemoryContext.set(distributorId, userId, chatModelName);

        try {
            long t0 = System.currentTimeMillis();
            String reply = chatDirect(distributorId, conversationId, userText);
            long durationMs = System.currentTimeMillis() - t0;

            // Persist user message
            chatMessageRepository.save(ChatMessage.builder()
                    .conversationId(conversationId)
                    .distributor(distributor)
                    .userId(userId)
                    .role(ChatRole.USER)
                    .content(userText)
                    .messageType(ChatMessageType.CHAT)
                    .modelName(chatModelName)
                    .build());

            // Persist assistant reply
            chatMessageRepository.save(ChatMessage.builder()
                    .conversationId(conversationId)
                    .distributor(distributor)
                    .userId(userId)
                    .role(ChatRole.ASSISTANT)
                    .content(reply)
                    .messageType(ChatMessageType.CHAT)
                    .modelName(chatModelName)
                    .durationMs(durationMs)
                    .build());

            evictHistoryCache(conversationId);

            return chatMessageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 1))
                    .getContent().get(0);

        } catch (Exception e) {
            log.error("Direct chat failed for conversation={}, falling back to agent: {}",
                    conversationId, e.getMessage());
            return chatWithAgentFallback(distributor, distributorId, userId, conversationId, userText);
        } finally {
            AssistantMemoryContext.clear();
        }
    }

    /**
     * PRIMARY: Fetch DB data in Java, build prompt, call LLM directly.
     * No LLM tool calling — works with any model.
     */
    private String chatDirect(UUID distributorId, UUID conversationId, String userText) {
        // 1. Fetch all business data directly from DB
        String businessContext = contextFetcher.fetchContext(distributorId);

        // 2. Load conversation history for context (last 20 messages)
        List<ChatMessage> history = chatMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (history.size() > 20) {
            history = history.subList(history.size() - 20, history.size());
        }

        // 3. Build LangChain4j message list
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(SYSTEM_PROMPT));

        for (ChatMessage prior : history) {
            if (prior.getRole() == ChatRole.USER) {
                messages.add(UserMessage.from(prior.getContent()));
            } else if (prior.getRole() == ChatRole.ASSISTANT) {
                messages.add(AiMessage.from(prior.getContent()));
            }
        }

        // 4. User message with pre-fetched data injected
        String userMessageWithData = businessContext + "\n\nUSER QUESTION: " + userText;
        messages.add(UserMessage.from(userMessageWithData));

        // 5. Plain LLM call — no tool calling required
        return chatLanguageModel.generate(messages).content().text();
    }

    /**
     * FALLBACK: Use LangChain4j AiServices with tool calling.
     * Handles its own persistence via AssistantChatMemoryStore.
     */
    private ChatMessage chatWithAgentFallback(Distributor distributor, UUID distributorId,
                                               UUID userId, UUID conversationId, String userText) {
        try {
            String contextualMessage = "DISTRIBUTOR_ID: " + distributorId + "\n\n" + userText;
            long t0 = System.currentTimeMillis();
            String reply = assistantAgent.chat(conversationId, contextualMessage);
            long durationMs = System.currentTimeMillis() - t0;

            updateLatestAssistantDuration(conversationId, durationMs);
            evictHistoryCache(conversationId);

            return chatMessageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 1))
                    .getContent().get(0);

        } catch (Exception e) {
            log.error("Agent fallback also failed for conversation={}: {}", conversationId, e.getMessage(), e);
            String errorReply = "I'm sorry, I encountered an error while processing your request. " +
                    "Please try again or contact support if the issue persists.";
            saveErrorAssistantMessage(conversationId, distributor, userId, errorReply);
            evictHistoryCache(conversationId);
            return chatMessageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 1))
                    .getContent().get(0);
        }
    }

    // ── Report ────────────────────────────────────────────────────────────────

    /**
     * Generate a structured report and persist it as a REPORT-type ASSISTANT message.
     *
     * Reports are NOT routed through the LangChain4j agent — data is gathered directly
     * from tools by AssistantReportBuilder, then sent to AssistantReportAiService.
     */
    @Transactional
    public ChatMessage generateReport(UUID distributorId, UUID userId,
                                      UUID conversationId, ReportType reportType,
                                      Map<String, Object> params) {

        Distributor distributor = loadDistributor(distributorId);

        String requestText = "Generate a " + reportType.name().replace("_", " ").toLowerCase() + " report";
        chatMessageRepository.save(ChatMessage.builder()
                .conversationId(conversationId)
                .distributor(distributor)
                .userId(userId)
                .role(ChatRole.USER)
                .content(requestText)
                .messageType(ChatMessageType.REPORT)
                .reportType(reportType)
                .reportParams(params)
                .modelName(reportModelName)
                .build());

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

        log.info("Generated {} report for distributor={} in {}ms", reportType, distributorId, durationMs);
        ChatMessage saved = chatMessageRepository.save(reportMsg);
        evictHistoryCache(conversationId);
        return saved;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateLatestAssistantDuration(UUID conversationId, long durationMs) {
        try {
            chatMessageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 5))
                    .getContent().stream()
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

    private void saveErrorAssistantMessage(UUID conversationId, Distributor distributor,
                                           UUID userId, String errorReply) {
        try {
            chatMessageRepository.save(ChatMessage.builder()
                    .conversationId(conversationId)
                    .distributor(distributor)
                    .userId(userId)
                    .role(ChatRole.ASSISTANT)
                    .content(errorReply)
                    .messageType(ChatMessageType.CHAT)
                    .modelName(chatModelName)
                    .build());
        } catch (Exception ex) {
            log.error("Could not save error assistant message for conversation={}", conversationId, ex);
        }
    }

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
