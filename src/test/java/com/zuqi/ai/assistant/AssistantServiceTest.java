package com.zuqi.ai.assistant;

import com.zuqi.domain.ai.ChatMessage;
import com.zuqi.domain.ai.ChatMessageType;
import com.zuqi.domain.ai.ChatRole;
import com.zuqi.domain.ai.ReportType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.ChatMessageRepository;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AssistantService}.
 *
 * Covers: chat happy path, ThreadLocal context lifecycle, message prefix,
 * graceful degradation on agent failure, Redis cache eviction,
 * report persistence, report type correctness, report builder failure,
 * and durationMs tracking.
 */
@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock private AssistantAgent            assistantAgent;
    @Mock private AssistantAgentFactory     assistantAgentFactory;
    @Mock private AssistantChatMemoryStore  chatMemoryStore;
    @Mock private AssistantReportBuilder    reportBuilder;
    @Mock private ChatMessageRepository     chatMessageRepository;
    @Mock private DistributorRepository     distributorRepository;
    @Mock private CacheManager              cacheManager;
    @Mock private Cache                     cache;
    @Mock private SecurityUtils             securityUtils;

    @InjectMocks
    private AssistantService service;

    private UUID distributorId;
    private UUID userId;
    private UUID conversationId;
    private Distributor distributor;

    @BeforeEach
    void setUp() {
        distributorId  = UUID.randomUUID();
        userId         = UUID.randomUUID();
        conversationId = UUID.randomUUID();

        distributor = new Distributor();
        distributor.setId(distributorId);
        distributor.setName("Test Distributor");

        // Inject @Value fields via reflection
        ReflectionTestUtils.setField(service, "chatModelName",   "qwen2.5-coder:32b");
        ReflectionTestUtils.setField(service, "reportModelName", "qwen2.5-coder:32b");

        // Factory always returns the mock agent (only used by chat() tests, not report tests)
        lenient().when(assistantAgentFactory.buildForRole(anyString())).thenReturn(assistantAgent);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Build a minimal persisted ChatMessage with ASSISTANT role. */
    private ChatMessage assistantMessage(String content) {
        return ChatMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .distributor(distributor)
                .userId(userId)
                .role(ChatRole.ASSISTANT)
                .content(content)
                .messageType(ChatMessageType.CHAT)
                .modelName("qwen2.5-coder:32b")
                .build();
    }

    /** Stub the distributor lookup to return {@link #distributor}. */
    private void stubDistributor() {
        when(distributorRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
    }

    /**
     * Stub repo to return the supplied messages from
     * findByConversationIdOrderByCreatedAtDesc for any Pageable.
     */
    private void stubRepoPage(ChatMessage... messages) {
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtDesc(
                eq(conversationId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(messages)));
    }

    // ── chat() tests ──────────────────────────────────────────────────────────

    @Test
    void chat_persistsMessagesAndReturnsAssistantReply() {
        stubDistributor();
        ChatMessage expected = assistantMessage("Sales are good");
        when(assistantAgent.chat(eq(conversationId), anyString())).thenReturn("Sales are good");
        stubRepoPage(expected);

        ChatMessage result = service.chat(distributorId, userId, conversationId, "How are sales?");

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Sales are good");
        assertThat(result.getRole()).isEqualTo(ChatRole.ASSISTANT);
    }

    @Test
    void chat_setsMemoryContextBeforeCallAndClearsAfter() {
        stubDistributor();
        ChatMessage reply = assistantMessage("OK");
        stubRepoPage(reply);

        AtomicReference<AssistantMemoryContext.MemoryContext> capturedCtx = new AtomicReference<>();

        when(assistantAgent.chat(eq(conversationId), anyString())).thenAnswer(inv -> {
            // Capture the ThreadLocal value while the agent call is in progress
            capturedCtx.set(AssistantMemoryContext.get());
            return "OK";
        });

        service.chat(distributorId, userId, conversationId, "Hello");

        // Context was set during the agent call
        assertThat(capturedCtx.get()).isNotNull();
        assertThat(capturedCtx.get().distributorId()).isEqualTo(distributorId);
        assertThat(capturedCtx.get().userId()).isEqualTo(userId);

        // Context is cleared after the call completes
        assertThat(AssistantMemoryContext.get()).isNull();
    }

    @Test
    void chat_prefixesDistributorIdInMessage() {
        stubDistributor();
        stubRepoPage(assistantMessage("reply"));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        when(assistantAgent.chat(eq(conversationId), messageCaptor.capture())).thenReturn("reply");

        service.chat(distributorId, userId, conversationId, "What are my sales?");

        String captured = messageCaptor.getValue();
        assertThat(captured).contains("DISTRIBUTOR_ID: " + distributorId);
    }

    @Test
    void chat_gracefulDegradationWhenAgentThrows() {
        stubDistributor();
        // Agent throws — saveErrorAssistantMessage calls loadDistributor again
        when(assistantAgent.chat(eq(conversationId), anyString()))
                .thenThrow(new RuntimeException("Ollama timeout"));
        stubRepoPage(assistantMessage("I'm sorry, I encountered an error"));
        // cacheManager is needed by evictHistoryCache even in error path
        when(cacheManager.getCache("chat-history")).thenReturn(cache);

        // Must not propagate the exception
        assertThatNoException().isThrownBy(() ->
                service.chat(distributorId, userId, conversationId, "crash please"));

        // save() must have been called at least once (the error assistant message)
        verify(chatMessageRepository, atLeastOnce()).save(any(ChatMessage.class));

        // The returned message is fetched from repo
        verify(chatMessageRepository, atLeastOnce())
                .findByConversationIdOrderByCreatedAtDesc(eq(conversationId), any(Pageable.class));
    }

    @Test
    void chat_evictsRedisCacheAfterSave() {
        stubDistributor();
        stubRepoPage(assistantMessage("reply"));
        when(assistantAgent.chat(eq(conversationId), anyString())).thenReturn("reply");
        when(cacheManager.getCache("chat-history")).thenReturn(cache);

        service.chat(distributorId, userId, conversationId, "evict test");

        verify(cache).evict(conversationId);
    }

    // ── generateReport() tests ────────────────────────────────────────────────

    @Test
    void generateReport_persistsUserAndAssistantTurns() {
        stubDistributor();
        when(reportBuilder.build(eq(distributorId), eq(ReportType.SALES), any()))
                .thenReturn("# Sales Report\n\nContent here.");
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cacheManager.getCache("chat-history")).thenReturn(cache);

        service.generateReport(distributorId, userId, conversationId,
                ReportType.SALES, Map.of("periodDays", 30));

        // Once for USER turn, once for ASSISTANT report turn
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    void generateReport_usesCorrectReportType() {
        stubDistributor();
        when(reportBuilder.build(eq(distributorId), eq(ReportType.SALES), any()))
                .thenReturn("# Sales Report");
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        when(chatMessageRepository.save(captor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cacheManager.getCache("chat-history")).thenReturn(cache);

        service.generateReport(distributorId, userId, conversationId,
                ReportType.SALES, Map.of());

        // The second saved message is the ASSISTANT report
        List<ChatMessage> saved = captor.getAllValues();
        ChatMessage reportMsg = saved.stream()
                .filter(m -> m.getRole() == ChatRole.ASSISTANT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ASSISTANT message saved"));

        assertThat(reportMsg.getReportType()).isEqualTo(ReportType.SALES);
        assertThat(reportMsg.getMessageType()).isEqualTo(ChatMessageType.REPORT);
    }

    @Test
    void generateReport_gracefulDegradationWhenBuilderThrows() {
        stubDistributor();
        when(reportBuilder.build(any(), any(), any()))
                .thenThrow(new RuntimeException("LLM unavailable"));
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        when(chatMessageRepository.save(captor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cacheManager.getCache("chat-history")).thenReturn(cache);

        assertThatNoException().isThrownBy(() ->
                service.generateReport(distributorId, userId, conversationId,
                        ReportType.INVENTORY, Map.of()));

        // ASSISTANT turn still saved with failure content
        ChatMessage assistantMsg = captor.getAllValues().stream()
                .filter(m -> m.getRole() == ChatRole.ASSISTANT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ASSISTANT message saved"));

        assertThat(assistantMsg.getContent()).startsWith("# Report Generation Failed");
    }

    @Test
    void generateReport_setsDurationMs() {
        stubDistributor();
        when(reportBuilder.build(any(), any(), any())).thenReturn("# Fast Report");
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        when(chatMessageRepository.save(captor.capture()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(cacheManager.getCache("chat-history")).thenReturn(cache);

        service.generateReport(distributorId, userId, conversationId,
                ReportType.PAYMENT, Map.of());

        ChatMessage reportMsg = captor.getAllValues().stream()
                .filter(m -> m.getRole() == ChatRole.ASSISTANT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ASSISTANT message saved"));

        assertThat(reportMsg.getDurationMs()).isNotNull();
        assertThat(reportMsg.getDurationMs()).isGreaterThanOrEqualTo(0L);
    }
}
