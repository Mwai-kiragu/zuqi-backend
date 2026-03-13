package com.zuqi.api.controller;

import com.zuqi.ai.assistant.AssistantService;
import com.zuqi.ai.assistant.ReportDataService;
import com.zuqi.api.dto.ApiResponse;
import com.zuqi.api.dto.assistant.ChatRequest;
import com.zuqi.api.dto.assistant.ConversationSummary;
import com.zuqi.api.dto.assistant.ReportDataResponse;
import com.zuqi.api.dto.assistant.ReportRequest;
import com.zuqi.domain.ai.ChatMessage;
import com.zuqi.domain.ai.ReportType;
import com.zuqi.repository.ChatMessageRepository;
import com.zuqi.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for the AI-powered assistant chat and report generation.
 *
 * Authorization is handled by Casbin (V115 migration) — no @PreAuthorize needed.
 *
 * Endpoints:
 *   POST /v1/ai/assistant/chat                         — send a chat message
 *   POST /v1/ai/assistant/report                       — generate a structured report
 *   GET  /v1/ai/assistant/history/{conversationId}     — full conversation history
 *   GET  /v1/ai/assistant/conversations/{distributorId}— list conversations for a distributor
 *   DELETE /v1/ai/assistant/history/{conversationId}   — clear a conversation
 */
@SuppressWarnings("DataFlowIssue")
@RestController
@RequestMapping("/v1/ai/assistant")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI - Assistant", description = "AI-powered chat assistant and business report generation")
public class AiAssistantController {

    private final AssistantService       assistantService;
    private final ReportDataService      reportDataService;
    private final ChatMessageRepository  chatMessageRepository;
    private final SecurityUtils          securityUtils;

    // ── POST /chat ────────────────────────────────────────────────────────

    @PostMapping("/chat")
    @Operation(
            summary = "Send a chat message to the AI assistant",
            description = "Sends a user question to the AI assistant. " +
                          "The assistant has access to sales, inventory, payment, credit, delivery, " +
                          "rep performance, and demand forecast data for the distributor.")
    public ResponseEntity<ApiResponse<ChatMessage>> chat(
            @Valid @RequestBody ChatRequest request) {

        UUID userId = securityUtils.getCurrentUserId();

        // SUPER_ADMIN may omit distributorId — fall back to their own distributor if set,
        // or reject if still null (no context available)
        UUID distributorId = request.getDistributorId() != null
                ? request.getDistributorId()
                : securityUtils.getCurrentUserDistributorId();
        if (distributorId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("distributorId is required"));
        }

        log.info("POST /v1/ai/assistant/chat distributor={} conversation={} user={}",
                distributorId, request.getConversationId(), userId);

        try {
            ChatMessage reply = assistantService.chat(
                    distributorId,
                    userId,
                    request.getConversationId(),
                    request.getMessage());

            return ResponseEntity.ok(ApiResponse.success(reply));

        } catch (IllegalArgumentException e) {
            log.warn("Chat rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Chat failed for conversation={}: {}", request.getConversationId(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Chat failed: " + e.getMessage()));
        }
    }

    // ── POST /report ──────────────────────────────────────────────────────

    @PostMapping("/report")
    @Operation(
            summary = "Generate a structured business report",
            description = "Generates a markdown-formatted report. " +
                          "Supported types: SALES, INVENTORY, PAYMENT, CREDIT_RISK, " +
                          "REP_PERFORMANCE, MERCHANT_SUMMARY, DEMAND_FORECAST, ANOMALY_SUMMARY. " +
                          "Optional params: {\"periodDays\": 30}")
    public ResponseEntity<ApiResponse<ChatMessage>> generateReport(
            @Valid @RequestBody ReportRequest request) {

        UUID userId = securityUtils.getCurrentUserId();
        Map<String, Object> params = request.getParams() != null ? request.getParams() : Collections.emptyMap();

        log.info("POST /v1/ai/assistant/report type={} distributor={} conversation={} user={}",
                request.getReportType(), request.getDistributorId(), request.getConversationId(), userId);

        try {
            ChatMessage report = assistantService.generateReport(
                    request.getDistributorId(),
                    userId,
                    request.getConversationId(),
                    request.getReportType(),
                    params);

            return ResponseEntity.ok(ApiResponse.success(report));

        } catch (IllegalArgumentException e) {
            log.warn("Report request rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Report generation failed type={} distributor={}: {}",
                    request.getReportType(), request.getDistributorId(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Report generation failed: " + e.getMessage()));
        }
    }

    // ── GET /history/{conversationId} ─────────────────────────────────────

    @GetMapping("/history/{conversationId}")
    @Operation(
            summary = "Get full conversation history",
            description = "Returns all messages in a conversation ordered oldest-first.")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getHistory(
            @Parameter(required = true, description = "Conversation UUID")
            @PathVariable UUID conversationId) {

        log.info("GET /v1/ai/assistant/history/{}", conversationId);

        try {
            List<ChatMessage> messages =
                    chatMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
            return ResponseEntity.ok(ApiResponse.success(messages));
        } catch (Exception e) {
            log.error("Failed to load history for conversation={}: {}", conversationId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to load history: " + e.getMessage()));
        }
    }

    // ── GET /conversations/{distributorId} ────────────────────────────────

    @GetMapping("/conversations/{distributorId}")
    @Operation(
            summary = "List conversations for a distributor",
            description = "Returns a summary of recent conversations (newest first). " +
                          "Each entry includes conversationId, message count, and a preview of the last user message.")
    public ResponseEntity<ApiResponse<List<ConversationSummary>>> listConversations(
            @Parameter(required = true, description = "Distributor UUID")
            @PathVariable UUID distributorId,
            @Parameter(description = "Page size (default 20)")
            @RequestParam(defaultValue = "20") int size) {

        UUID userId = securityUtils.getCurrentUserId();
        log.info("GET /v1/ai/assistant/conversations/{} user={}", distributorId, userId);

        try {
            List<UUID> conversationIds = chatMessageRepository
                    .findConversationIdsByDistributorAndUser(
                            distributorId, userId,
                            PageRequest.of(0, size, Sort.by("createdAt").descending()));

            List<ConversationSummary> summaries = conversationIds.stream()
                    .map(convId -> buildSummary(convId, distributorId, userId))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(summaries));

        } catch (Exception e) {
            log.error("Failed to list conversations for distributor={}: {}", distributorId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to list conversations: " + e.getMessage()));
        }
    }

    // ── DELETE /history/{conversationId} ──────────────────────────────────

    @DeleteMapping("/history/{conversationId}")
    @Operation(
            summary = "Delete a conversation",
            description = "Permanently deletes all messages in the specified conversation.")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @Parameter(required = true, description = "Conversation UUID")
            @PathVariable UUID conversationId) {

        log.info("DELETE /v1/ai/assistant/history/{}", conversationId);

        try {
            chatMessageRepository.deleteByConversationId(conversationId);
            return ResponseEntity.ok(ApiResponse.success("Conversation deleted"));
        } catch (Exception e) {
            log.error("Failed to delete conversation={}: {}", conversationId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to delete conversation: " + e.getMessage()));
        }
    }

    // ── GET /report-data ──────────────────────────────────────────────────────

    @GetMapping("/report-data")
    @Operation(
            summary = "Get structured report data as JSON",
            description = "Returns raw structured JSON data for a report type. " +
                          "Unlike /report which sends data to the LLM for narrative generation, " +
                          "this endpoint returns the raw data so the frontend can render it " +
                          "in any format (table, PDF, CSV, Markdown). " +
                          "Supported types: SALES, INVENTORY, PAYMENT, CREDIT_RISK, " +
                          "REP_PERFORMANCE, MERCHANT_SUMMARY, DEMAND_FORECAST, ANOMALY_SUMMARY.")
    public ResponseEntity<ApiResponse<ReportDataResponse>> getReportData(
            @Parameter(required = true) @RequestParam UUID distributorId,
            @Parameter(required = true) @RequestParam ReportType type,
            @Parameter(description = "Look-back period in days (default 30)")
            @RequestParam(defaultValue = "30") int periodDays) {

        log.info("GET /v1/ai/assistant/report-data type={} distributor={}", type, distributorId);

        try {
            ReportDataResponse data = reportDataService.getReportData(distributorId, type, periodDays);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (IllegalArgumentException e) {
            log.warn("Report data rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Report data failed type={} distributor={}: {}", type, distributorId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get report data: " + e.getMessage()));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private ConversationSummary buildSummary(UUID conversationId, UUID distributorId, UUID userId) {
        try {
            long count = chatMessageRepository.countByConversationId(conversationId);

            // Last message in the conversation for timestamp + preview
            List<ChatMessage> recent = chatMessageRepository
                    .findByConversationIdOrderByCreatedAtDesc(
                            conversationId,
                            PageRequest.of(0, 1, Sort.by("createdAt").descending()))
                    .getContent();

            if (recent.isEmpty()) {
                return ConversationSummary.builder()
                        .conversationId(conversationId)
                        .messageCount(count)
                        .build();
            }

            ChatMessage last = recent.get(0);
            String preview = last.getContent() != null && last.getContent().length() > 120
                    ? last.getContent().substring(0, 120) + "…"
                    : last.getContent();

            return ConversationSummary.builder()
                    .conversationId(conversationId)
                    .messageCount(count)
                    .lastMessageAt(last.getCreatedAt())
                    .lastUserMessage(preview)
                    .build();

        } catch (Exception e) {
            log.warn("Could not build summary for conversation={}: {}", conversationId, e.getMessage());
            return ConversationSummary.builder().conversationId(conversationId).build();
        }
    }
}
