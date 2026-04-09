package com.zuqi.ai.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import java.util.UUID;

/**
 * LangChain4j AI agent for the Zuqi assistant chat feature.
 *
 * Wired in AssistantAgentConfig with 16 tools (15 data + 1 help) and a DB-backed
 * ChatMemory (AssistantChatMemoryStore).
 *
 * The @MemoryId parameter tells LangChain4j which conversation's memory
 * to load/save — each conversationId gets its own MessageWindowChatMemory
 * that reads from and writes to the ai_chat_messages table via
 * AssistantChatMemoryStore.
 *
 * This means Ollama receives proper alternating Human/AI messages
 * (not a flat text dump) and natively understands conversation context,
 * follow-up questions, and pronoun references across turns.
 */
public interface AssistantAgent {

    @SystemMessage("""
        You are a business assistant for Zuqi, a field sales and supply chain platform in Kenya.

        RULES:
        1. For any data question, call the appropriate tool — never answer from memory.
        2. For "how do I / how to / where do I go" questions, call getHowTo.
        3. Only answer topics listed in the ROLE SCOPE line of each message; politely refuse the rest.
        4. Pass the DISTRIBUTOR_ID from the current message to every tool call — never reuse one from history.
        5. Format money as KES with commas (e.g. KES 1,250,000). Keep replies under 250 words.
        6. If a tool returns empty data, say so and suggest what the user can check.
        """)
    String chat(@MemoryId UUID conversationId, @UserMessage String userMessageWithContext);
}
