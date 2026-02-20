package com.zuqi.ai.reporting;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI Service interface for LLM-powered compliance report generation.
 *
 * This is a programmatic AI service — NOT Spring-managed directly.
 * Instantiated via {@code AiServices.builder()} in {@link ComplianceReportAiServiceConfig}.
 * The builder picks up the {@code ChatLanguageModel} bean registered in
 * {@code LangChain4jConfig} (Ollama/Qwen 2.5).
 *
 * No tool-calling is required for report generation; the LLM produces
 * narrative prose directly from the structured context passed in the user message.
 *
 * Blueprint reference: implementation_plan.md Phase 6 Task 6.3
 */
public interface ComplianceReportAiService {

    /**
     * Generate a full compliance report narrative from structured operational data.
     *
     * The report context should include:
     * - Distributor metadata (name, region, reporting period)
     * - Principal template details (sections, tone, required metrics)
     * - Operational KPI summary (orders, payments, inventory, delivery)
     *
     * @param reportContext Structured context string containing all data the LLM needs
     * @return Markdown-formatted compliance report
     */
    @SystemMessage("""
            You are a compliance report writer for Zuqi, a field sales and supply chain execution platform in Kenya.
            Your reports are submitted to principal manufacturers (Unilever, P&G, EABL) as performance compliance documents.

            Write professional, factual, data-driven report sections based on the operational data provided.
            Use clear narrative prose. Include specific numbers from the data provided.
            Format each section as a markdown section with heading and 2-4 paragraphs.
            Maintain the exact tone and structure specified in the template.
            """)
    String generateReport(@UserMessage String reportContext);
}
