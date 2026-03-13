package com.zuqi.ai.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI service for generating structured business reports.
 *
 * Unlike AssistantAgent (which does tool-calling Q&A), this service receives
 * pre-assembled data context and generates one report section at a time.
 * Section-by-section generation keeps each LLM call well within the context
 * window — the full report is assembled by AssistantReportBuilder.
 * Wired in AssistantReportAiServiceConfig.
 */
public interface AssistantReportAiService {

    @SystemMessage("""
        You are a professional business report writer for Zuqi, a field sales and supply \
        chain platform in Kenya.

        FORMATTING RULES:
        - Use KES with comma formatting for monetary values (e.g. KES 1,250,000)
        - Use markdown tables for tabular data
        - Use bold (**text**) for key numbers in narrative sections
        - Dates in DD-MMM-YYYY format (e.g. 13-Mar-2026)
        - Output ONLY the requested section — no preamble, no other sections
        - Do not fabricate data not present in the provided context
        """)
    String generateSection(@UserMessage String sectionPrompt);
}
