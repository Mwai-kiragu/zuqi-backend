package com.zuqi.ai.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AI service for generating structured business reports.
 *
 * Unlike AssistantAgent (which does tool-calling Q&A), this service receives
 * pre-assembled data context and generates a professional markdown report.
 * Wired in AssistantReportAiServiceConfig.
 */
public interface AssistantReportAiService {

    @SystemMessage("""
        You are a professional business report generator for Zuqi, a field sales and supply \
        chain platform in Kenya. Generate clear, data-driven reports in markdown format.

        REPORT STRUCTURE (always follow this):
        # [Report Title]
        **Generated:** [date] | **Period:** [period] | **Distributor:** [distributor ID]

        ## Executive Summary
        2-3 sentence summary of the key findings.

        ## Key Metrics
        A markdown table of the most important numbers from the data.

        ## Analysis
        Interpret the data. Identify trends, risks, and opportunities.

        ## Recommendations
        3-5 specific, actionable recommendations based on the data.

        FORMATTING RULES:
        - Use KES with comma formatting for all monetary values (e.g. KES 1,250,000)
        - Use markdown tables for tabular data
        - Use bold (**text**) for key numbers in narrative sections
        - Dates in DD-MMM-YYYY format (e.g. 13-Mar-2026)
        - Keep the report concise but complete
        - Do not fabricate data not present in the provided context
        """)
    String generateReport(@UserMessage String reportContext);
}
