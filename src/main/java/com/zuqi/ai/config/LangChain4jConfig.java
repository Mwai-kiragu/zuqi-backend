package com.zuqi.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j configuration for Ollama-based LLM integration.
 *
 * Configures local LLM deployment for credit scoring and AI agent capabilities.
 * Uses Ollama server for all LLM operations (no cloud provider fallback).
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.1
 */
@Configuration
@Slf4j
public class LangChain4jConfig {

    @Value("${langchain4j.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String chatModelName;

    @Value("${langchain4j.ollama.chat-model.temperature}")
    private Double temperature;

    @Value("${langchain4j.ollama.chat-model.timeout}")
    private Duration chatTimeout;

    @Value("${langchain4j.ollama.embedding-model.model-name}")
    private String embeddingModelName;

    @Value("${langchain4j.ollama.embedding-model.timeout}")
    private Duration embeddingTimeout;

    /**
     * Ollama-based chat model for credit scoring and AI agent.
     *
     * Low temperature (0.1) ensures consistent, deterministic outputs
     * for credit evaluation and operational recommendations.
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing Ollama chat model: {} at {}", chatModelName, ollamaBaseUrl);

        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(chatModelName)
                .temperature(temperature)
                .timeout(chatTimeout)
                .build();
    }

    /**
     * Ollama-based embedding model for RAG (Retrieval-Augmented Generation).
     *
     * Used for:
     * - Merchant profile embeddings (credit scoring context)
     * - Recommendation history embeddings (agent memory)
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Initializing Ollama embedding model: {} at {}", embeddingModelName, ollamaBaseUrl);

        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModelName)
                .timeout(embeddingTimeout)
                .build();
    }
}
