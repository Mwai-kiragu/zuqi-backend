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

import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates AI assistant chat and report generation.
 *
 * Chat strategy
 * -------------
 * Uses LangChain4j AiServices tool calling exclusively (qwen2.5:7b supports function calling).
 * The role-scoped agent built by AssistantAgentFactory only has tools registered for
 * the caller's role — so tool filtering enforces data access at the LLM level (Layer 1 security).
 * The system prompt provides scope guidance (Layer 2).
 * Casbin policies protect the endpoint (Layer 3).
 *
 * Reports
 * -------
 * Report generation uses AssistantReportBuilder which fetches structured data and calls
 * the LLM section-by-section for narrative prose — this is intentional and separate from chat.
 */
@SuppressWarnings("DataFlowIssue")
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantService {

    private static final String HISTORY_CACHE = "chat-history";

    private final AssistantAgentFactory     assistantAgentFactory;
    private final AssistantChatMemoryStore  chatMemoryStore;
    private final AssistantReportBuilder    reportBuilder;
    private final ChatMessageRepository     chatMessageRepository;
    private final DistributorRepository     distributorRepository;
    private final CacheManager              cacheManager;
    private final com.zuqi.util.SecurityUtils securityUtils;

    @Value("${langchain4j.rbs-ai.chat-model.model-name}")
    private String chatModelName;

    @Value("${langchain4j.rbs-ai.report-model.model-name}")
    private String reportModelName;

    // ── Chat ─────────────────────────────────────────────────────────────────

    /**
     * Process a single chat turn using LangChain4j tool calling.
     * The agent calls only the tools registered for the user's role.
     * Persistence is handled by AssistantChatMemoryStore.
     */
    public ChatMessage chat(UUID distributorId, UUID userId,
                            UUID conversationId, String userText) {

        Distributor distributor = loadDistributor(distributorId);
        String primaryRole = resolvePrimaryRole();
        AssistantMemoryContext.set(distributorId, userId, chatModelName);

        try {
            AssistantAgent roleAgent = assistantAgentFactory.buildForRole(primaryRole);
            // Prefix role context so the LLM stays within scope
            String contextualMessage = "USER ROLE: " + primaryRole +
                    "\nDISTRIBUTOR_ID: " + distributorId +
                    "\nROLE SCOPE: " + getRoleScopeInstruction(primaryRole) +
                    "\n\n" + userText;

            long t0 = System.currentTimeMillis();
            String reply = roleAgent.chat(conversationId, contextualMessage);
            long durationMs = System.currentTimeMillis() - t0;

            updateLatestAssistantDuration(conversationId, durationMs);
            evictHistoryCache(conversationId);

            return chatMessageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 1))
                    .getContent().get(0);

        } catch (Exception e) {
            log.error("Agent chat failed for conversation={}: {}", conversationId, e.getMessage(), e);
            String errorReply = "I'm sorry, I encountered an error while processing your request. " +
                    "Please try again or contact support if the issue persists.";
            saveErrorAssistantMessage(conversationId, distributor, userId, errorReply);
            evictHistoryCache(conversationId);
            return chatMessageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, 1))
                    .getContent().get(0);
        } finally {
            AssistantMemoryContext.clear();
            chatMemoryStore.clearTurnBuffer(conversationId);
        }
    }

    // ── Report ────────────────────────────────────────────────────────────────

    /**
     * Generate a structured report and persist it as a REPORT-type ASSISTANT message.
     * AssistantReportBuilder fetches structured data then calls the LLM section-by-section.
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

    /**
     * Returns a role-specific scope instruction injected into every chat turn.
     * This is Layer 2 of the three-layer AI security model — the tool restriction
     * in RoleAwareToolProvider is Layer 1.  Even if the LLM tries to answer from
     * training data it is explicitly told not to.
     */
    private String getRoleScopeInstruction(String role) {
        return switch (role.toUpperCase()) {
            case "DRIVER"            -> "Scope: deliveries, assigned orders, routes only. Refuse all other topics.";
            case "SALES_REP"         -> "Scope: sales, customers, orders, demand forecasts, invoices, deliveries. Refuse finance/accounting/warehouse topics.";
            case "WAREHOUSE_MANAGER" -> "Scope: inventory, stock transfers, procurement, POS, reorder, expiry, anomaly alerts. Refuse financial statements and credit topics.";
            case "FINANCE"           -> "Scope: payments, invoices, credit, expenses, funds transfers, GL, P&L, balance sheet, cash flow, AR/AP aging. Refuse warehouse and routing topics.";
            case "MERCHANT"          -> "Scope: this merchant's own orders, payments, invoices, credit score, and order suggestions only. Never expose other merchants' data.";
            case "CUSTOMER"          -> "Scope: this customer's own orders, payments, and invoices only.";
            case "MERCHANT_ADMIN"    -> "Scope: full access to this organisation's sales, inventory, payments, invoices, expenses, and procurement.";
            default                  -> "Scope: full access to all tools for this distributor.";
        };
    }

    /**
     * Resolve the primary role of the currently authenticated user.
     * Priority: SUPER_ADMIN > DISTRIBUTOR_ADMIN > FINANCE > WAREHOUSE_MANAGER > SALES_REP > DRIVER > MERCHANT_ADMIN > CUSTOMER.
     */
    private String resolvePrimaryRole() {
        try {
            com.zuqi.domain.user.User user = securityUtils.getCurrentUser();
            if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) return "UNKNOWN";
            java.util.List<String> roleNames = user.getRoles().stream()
                    .map(r -> r.getName())
                    .toList();
            for (String r : java.util.List.of("SUPER_ADMIN", "DISTRIBUTOR_ADMIN", "FINANCE",
                    "WAREHOUSE_MANAGER", "SALES_REP", "DRIVER", "MERCHANT_ADMIN", "CUSTOMER")) {
                if (roleNames.contains(r)) return r;
            }
            return roleNames.get(0);
        } catch (Exception e) {
            log.warn("Could not resolve primary role: {}", e.getMessage());
            return "UNKNOWN";
        }
    }
}
