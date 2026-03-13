package com.zuqi.api.controller;

import com.zuqi.ai.assistant.AssistantService;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.assistant.ChatRequest;
import com.zuqi.api.dto.assistant.ConversationSummary;
import com.zuqi.api.dto.assistant.ReportRequest;
import com.zuqi.domain.ai.ChatMessage;
import com.zuqi.domain.ai.ChatMessageType;
import com.zuqi.domain.ai.ChatRole;
import com.zuqi.domain.ai.ReportType;
import com.zuqi.repository.ChatMessageRepository;
import com.zuqi.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiAssistantController}.
 *
 * Covers all 5 endpoints: POST /chat, POST /report, GET /history/{id},
 * GET /conversations/{distributorId}, DELETE /history/{id}.
 */
@ExtendWith(MockitoExtension.class)
class AiAssistantControllerTest {

    @Mock private AssistantService       assistantService;
    @Mock private ChatMessageRepository  chatMessageRepository;
    @Mock private SecurityUtils          securityUtils;

    @InjectMocks
    private AiAssistantController controller;

    // ── POST /chat ────────────────────────────────────────────────────────

    @Test
    void chat_returns200WithAssistantReply() {
        UUID userId         = UUID.randomUUID();
        UUID distributorId  = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(securityUtils.getCurrentUserId()).thenReturn(userId);

        ChatMessage reply = buildChatMessage(conversationId, ChatRole.ASSISTANT,
                "Sales are up 15%", ChatMessageType.CHAT);
        when(assistantService.chat(eq(distributorId), eq(userId), eq(conversationId), anyString()))
                .thenReturn(reply);

        ChatRequest request = new ChatRequest();
        request.setDistributorId(distributorId);
        request.setConversationId(conversationId);
        request.setMessage("How are sales this week?");

        ResponseEntity<ApiResponse<ChatMessage>> response = controller.chat(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getContent()).isEqualTo("Sales are up 15%");
    }

    @Test
    void chat_returns400WhenDistributorNotFound() {
        UUID userId         = UUID.randomUUID();
        UUID distributorId  = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(assistantService.chat(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Distributor not found"));

        ChatRequest request = new ChatRequest();
        request.setDistributorId(distributorId);
        request.setConversationId(conversationId);
        request.setMessage("What is my stock level?");

        ResponseEntity<ApiResponse<ChatMessage>> response = controller.chat(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("Distributor not found");
    }

    @Test
    void chat_returns500WhenServiceThrows() {
        UUID userId         = UUID.randomUUID();
        UUID distributorId  = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(assistantService.chat(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("LLM error"));

        ChatRequest request = new ChatRequest();
        request.setDistributorId(distributorId);
        request.setConversationId(conversationId);
        request.setMessage("Summarize my performance");

        ResponseEntity<ApiResponse<ChatMessage>> response = controller.chat(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    // ── POST /report ──────────────────────────────────────────────────────

    @Test
    void generateReport_returns200WithReport() {
        UUID userId         = UUID.randomUUID();
        UUID distributorId  = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(securityUtils.getCurrentUserId()).thenReturn(userId);

        ChatMessage reportMessage = buildChatMessage(conversationId, ChatRole.ASSISTANT,
                "# Sales Report\n\nRevenue grew 20% this month.", ChatMessageType.REPORT);
        when(assistantService.generateReport(eq(distributorId), eq(userId), eq(conversationId),
                eq(ReportType.SALES), any()))
                .thenReturn(reportMessage);

        ReportRequest request = new ReportRequest();
        request.setDistributorId(distributorId);
        request.setConversationId(conversationId);
        request.setReportType(ReportType.SALES);
        request.setParams(Map.of("periodDays", 30));

        ResponseEntity<ApiResponse<ChatMessage>> response = controller.generateReport(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
    }

    @Test
    void generateReport_usesEmptyParamsWhenNull() {
        UUID userId         = UUID.randomUUID();
        UUID distributorId  = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(securityUtils.getCurrentUserId()).thenReturn(userId);

        ChatMessage reportMessage = buildChatMessage(conversationId, ChatRole.ASSISTANT,
                "# Inventory Report", ChatMessageType.REPORT);
        when(assistantService.generateReport(eq(distributorId), eq(userId), eq(conversationId),
                eq(ReportType.INVENTORY), eq(Collections.emptyMap())))
                .thenReturn(reportMessage);

        ReportRequest request = new ReportRequest();
        request.setDistributorId(distributorId);
        request.setConversationId(conversationId);
        request.setReportType(ReportType.INVENTORY);
        request.setParams(null); // explicitly null — controller should use empty map

        ResponseEntity<ApiResponse<ChatMessage>> response = controller.generateReport(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(assistantService).generateReport(
                eq(distributorId), eq(userId), eq(conversationId),
                eq(ReportType.INVENTORY), eq(Collections.emptyMap()));
    }

    @Test
    void generateReport_returns400WhenDistributorNotFound() {
        UUID userId         = UUID.randomUUID();
        UUID distributorId  = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        when(assistantService.generateReport(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Distributor not found: " + distributorId));

        ReportRequest request = new ReportRequest();
        request.setDistributorId(distributorId);
        request.setConversationId(conversationId);
        request.setReportType(ReportType.PAYMENT);

        ResponseEntity<ApiResponse<ChatMessage>> response = controller.generateReport(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    // ── GET /history/{conversationId} ─────────────────────────────────────

    @Test
    void getHistory_returns200WithMessages() {
        UUID conversationId = UUID.randomUUID();

        ChatMessage msg1 = buildChatMessage(conversationId, ChatRole.USER,
                "Hello assistant", ChatMessageType.CHAT);
        ChatMessage msg2 = buildChatMessage(conversationId, ChatRole.ASSISTANT,
                "Hello! How can I help?", ChatMessageType.CHAT);

        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of(msg1, msg2));

        ResponseEntity<ApiResponse<List<ChatMessage>>> response = controller.getHistory(conversationId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(2);
    }

    @Test
    void getHistory_returns200WithEmptyListForNewConversation() {
        UUID conversationId = UUID.randomUUID();

        when(chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(Collections.emptyList());

        ResponseEntity<ApiResponse<List<ChatMessage>>> response = controller.getHistory(conversationId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEmpty();
    }

    // ── GET /conversations/{distributorId} ────────────────────────────────

    @Test
    void listConversations_returns200WithSummaries() {
        UUID userId        = UUID.randomUUID();
        UUID distributorId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        when(securityUtils.getCurrentUserId()).thenReturn(userId);

        // findConversationIdsByDistributorAndUser returns List<UUID>
        when(chatMessageRepository.findConversationIdsByDistributorAndUser(
                eq(distributorId), eq(userId), any(Pageable.class)))
                .thenReturn(List.of(conversationId));

        // countByConversationId
        when(chatMessageRepository.countByConversationId(conversationId)).thenReturn(5L);

        // findByConversationIdOrderByCreatedAtDesc returns Page<ChatMessage>
        ChatMessage lastMessage = buildChatMessage(conversationId, ChatRole.USER,
                "Show me the sales report", ChatMessageType.CHAT);
        when(chatMessageRepository.findByConversationIdOrderByCreatedAtDesc(
                eq(conversationId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lastMessage)));

        ResponseEntity<ApiResponse<List<ConversationSummary>>> response =
                controller.listConversations(distributorId, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);

        ConversationSummary summary = response.getBody().getData().get(0);
        assertThat(summary.getConversationId()).isEqualTo(conversationId);
        assertThat(summary.getMessageCount()).isEqualTo(5L);
    }

    // ── DELETE /history/{conversationId} ──────────────────────────────────

    @Test
    void deleteConversation_returns200() {
        UUID conversationId = UUID.randomUUID();

        doNothing().when(chatMessageRepository).deleteByConversationId(conversationId);

        ResponseEntity<ApiResponse<Void>> response = controller.deleteConversation(conversationId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(chatMessageRepository).deleteByConversationId(conversationId);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private ChatMessage buildChatMessage(UUID conversationId, ChatRole role,
                                         String content, ChatMessageType type) {
        return ChatMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .messageType(type)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
