package com.zuqi.ai.assistant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that AssistantAgent calls its data tools
 * and returns real answers from the database, not training-data guesses.
 *
 * Requires:
 *   - Ollama running at configured base-url with qwen2.5:14b
 *   - PostgreSQL (AWS RDS) reachable
 *   - Redis running
 *
 * Run with: ./mvnw test -Dtest=AssistantAgentIntegrationTest
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("ollama")
class AssistantAgentIntegrationTest {

    @Autowired
    private AssistantAgent assistantAgent;

    private static final UUID DISTRIBUTOR_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    // Real DISTRIBUTOR_ADMIN user seen in logs (d3eebc99-...)
    private static final UUID TEST_USER_ID   = UUID.fromString("d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a44");

    /**
     * Ask a sales question — the LLM must call getSalesTrend and return real DB data.
     * If tool calling is broken the LLM says "I don't have access to your database".
     */
    @Test
    void salesQuestion_shouldReturnRealDataNotTrainingGuess() {
        UUID conversationId = UUID.randomUUID();
        AssistantMemoryContext.set(DISTRIBUTOR_ID, TEST_USER_ID, "qwen2.5:14b");
        try {
            String reply = assistantAgent.chat(conversationId,
                    "DISTRIBUTOR_ID: " + DISTRIBUTOR_ID + "\n\nHow many orders do we have this month?");

            System.out.println("=== Sales reply ===\n" + reply);

            assertThat(reply).isNotBlank();
            assertThat(reply.toLowerCase())
                    .as("LLM should not say it lacks DB access — tool must have been called")
                    .doesNotContain("i don't have access")
                    .doesNotContain("i cannot access")
                    .doesNotContain("i don't have direct access")
                    .doesNotContain("no access to your")
                    .doesNotContain("unable to retrieve");
        } finally {
            AssistantMemoryContext.clear();
        }
    }

    @Test
    void inventoryQuestion_shouldReturnRealStockData() {
        UUID conversationId = UUID.randomUUID();
        AssistantMemoryContext.set(DISTRIBUTOR_ID, TEST_USER_ID, "qwen2.5:14b");
        try {
            String reply = assistantAgent.chat(conversationId,
                    "DISTRIBUTOR_ID: " + DISTRIBUTOR_ID + "\n\nWhat is our current stock situation?");

            System.out.println("=== Inventory reply ===\n" + reply);

            assertThat(reply).isNotBlank();
            assertThat(reply.toLowerCase())
                    .doesNotContain("i don't have access")
                    .doesNotContain("i cannot access")
                    .doesNotContain("unable to retrieve");
        } finally {
            AssistantMemoryContext.clear();
        }
    }

    @Test
    void paymentQuestion_shouldReturnRealPaymentData() {
        UUID conversationId = UUID.randomUUID();
        AssistantMemoryContext.set(DISTRIBUTOR_ID, TEST_USER_ID, "qwen2.5:14b");
        try {
            String reply = assistantAgent.chat(conversationId,
                    "DISTRIBUTOR_ID: " + DISTRIBUTOR_ID + "\n\nAny overdue payments or outstanding balances?");

            System.out.println("=== Payment reply ===\n" + reply);

            assertThat(reply).isNotBlank();
            assertThat(reply.toLowerCase())
                    .doesNotContain("i don't have access")
                    .doesNotContain("i cannot access");
        } finally {
            AssistantMemoryContext.clear();
        }
    }

    @Test
    void broadSummaryQuestion_shouldCallMultipleToolsAndSynthesize() {
        UUID conversationId = UUID.randomUUID();
        AssistantMemoryContext.set(DISTRIBUTOR_ID, TEST_USER_ID, "qwen2.5:14b");
        try {
            String reply = assistantAgent.chat(conversationId,
                    "DISTRIBUTOR_ID: " + DISTRIBUTOR_ID +
                    "\n\nGive me a quick business summary — orders, payments, and stock.");

            System.out.println("=== Broad summary reply ===\n" + reply);

            assertThat(reply).isNotBlank();
            // A broad question should produce a multi-topic answer
            assertThat(reply.toLowerCase())
                    .containsAnyOf("order", "payment", "stock", "inventory", "revenue", "kes");
        } finally {
            AssistantMemoryContext.clear();
        }
    }
}
