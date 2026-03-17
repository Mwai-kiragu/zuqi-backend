package com.zuqi.ai.agent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HelpTool}.
 *
 * Covers: exact match, prefix stripping, substring match, keyword match,
 * null/blank input fallback, and unknown action fallback.
 */
class HelpToolTest {

    private HelpTool helpTool;

    @BeforeEach
    void setUp() {
        helpTool = new HelpTool();
    }

    // ── Exact match ───────────────────────────────────────────────────────────

    @Test
    void exactMatch_createOrder_returnsSteps() {
        String result = helpTool.getHowTo("create order");
        assertThat(result).contains("Create an Order");
        assertThat(result).contains("steps");
    }

    @Test
    void exactMatch_recordPayment_returnsSteps() {
        String result = helpTool.getHowTo("record payment");
        assertThat(result).contains("Record a Payment");
        assertThat(result).contains("M-Pesa");
    }

    @Test
    void exactMatch_posSale_returnsSteps() {
        String result = helpTool.getHowTo("pos sale");
        assertThat(result).contains("POS");
        assertThat(result).contains("steps");
    }

    // ── Prefix stripping ──────────────────────────────────────────────────────

    @Test
    void prefixStrip_howDoICreateOrder() {
        String result = helpTool.getHowTo("how do i create order");
        assertThat(result).contains("Create an Order");
    }

    @Test
    void prefixStrip_howToRecordPayment() {
        String result = helpTool.getHowTo("how to record payment");
        assertThat(result).contains("Record a Payment");
    }

    @Test
    void prefixStrip_stepsToCheckStock() {
        String result = helpTool.getHowTo("steps to check stock");
        assertThat(result).contains("Check Stock");
    }

    @Test
    void prefixStrip_guideForStockTransfer() {
        String result = helpTool.getHowTo("guide for stock transfer");
        assertThat(result).contains("Stock Transfer");
    }

    @Test
    void prefixStrip_howCanIAddCustomer() {
        String result = helpTool.getHowTo("how can i add customer");
        assertThat(result).contains("Add a Customer");
    }

    // ── Substring / keyword match ─────────────────────────────────────────────

    @Test
    void substringMatch_invoiceInQuery() {
        String result = helpTool.getHowTo("tell me about invoice");
        assertThat(result).contains("Invoice");
    }

    @Test
    void keywordMatch_procurementKeyword() {
        String result = helpTool.getHowTo("purchase order process");
        assertThat(result).contains("Purchase Order");
    }

    @Test
    void keywordMatch_deliveryKeyword() {
        String result = helpTool.getHowTo("create delivery route");
        assertThat(result).contains("Delivery");
    }

    @Test
    void keywordMatch_expenseKeyword() {
        String result = helpTool.getHowTo("record expense claim");
        assertThat(result).contains("Expense");
    }

    // ── Null / blank fallback ─────────────────────────────────────────────────

    @Test
    void nullInput_returnsAvailableGuides() {
        String result = helpTool.getHowTo(null);
        assertThat(result).contains("available");
        assertThat(result).contains("create order");
    }

    @Test
    void blankInput_returnsAvailableGuides() {
        String result = helpTool.getHowTo("   ");
        assertThat(result).contains("available");
    }

    // ── Unknown action fallback ───────────────────────────────────────────────

    @Test
    void unknownAction_returnsAvailableGuidesList() {
        String result = helpTool.getHowTo("do something completely unknown xyz");
        assertThat(result).contains("available");
        assertThat(result).contains("create order");
    }

    // ── Content sanity checks ─────────────────────────────────────────────────

    @Test
    void createOrder_containsCreditWarningTip() {
        String result = helpTool.getHowTo("create order");
        assertThat(result).contains("credit");
    }

    @Test
    void checkStock_mentionsAIStockoutRisk() {
        String result = helpTool.getHowTo("check stock");
        assertThat(result).contains("Stockout Risk");
    }

    @Test
    void anomalyAlerts_containsSeverityLevels() {
        String result = helpTool.getHowTo("anomaly alerts");
        assertThat(result).contains("CRITICAL");
        assertThat(result).contains("HIGH");
    }

    @Test
    void demandForecast_mentionsForecastDate() {
        String result = helpTool.getHowTo("demand forecast");
        assertThat(result).contains("forecast");
    }

    @Test
    void generateReport_mentionsChatOption() {
        String result = helpTool.getHowTo("generate report");
        assertThat(result).contains("chat");
    }
}
