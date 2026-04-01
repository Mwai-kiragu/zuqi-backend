package com.zuqi.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
// import dev.langchain4j.model.embedding.EmbeddingModel;
// import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * LangChain4j configuration — RBS AI as primary LLM provider.
 *
 * RBS AI is an OpenAI-compatible vLLM server (Qwen3 14B).
 * Embedding model is commented out until RBS AI exposes an embeddings endpoint.
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.1
 */
@Configuration
@Slf4j
public class LangChain4jConfig {

    @Value("${langchain4j.rbs-ai.base-url}")
    private String rbsAiBaseUrl;

    @Value("${langchain4j.rbs-ai.api-key}")
    private String rbsAiApiKey;

    @Value("${langchain4j.rbs-ai.chat-model.model-name}")
    private String chatModelName;

    @Value("${langchain4j.rbs-ai.chat-model.temperature}")
    private Double temperature;

    @Value("${langchain4j.rbs-ai.chat-model.timeout}")
    private Duration chatTimeout;

    @Value("${langchain4j.rbs-ai.report-model.model-name}")
    private String reportModelName;

    @Value("${langchain4j.rbs-ai.report-model.temperature}")
    private Double reportTemperature;

    @Value("${langchain4j.rbs-ai.report-model.timeout}")
    private Duration reportTimeout;

    // Embedding model config — uncomment when RBS AI exposes an embeddings endpoint
    // @Value("${langchain4j.rbs-ai.embedding-model.model-name}")
    // private String embeddingModelName;

    // @Value("${langchain4j.rbs-ai.embedding-model.timeout}")
    // private Duration embeddingTimeout;

    /**
     * RBS AI chat model for credit scoring and AI agent.
     *
     * Low temperature (0.1) ensures consistent, deterministic outputs
     * for credit evaluation and operational recommendations.
     */
    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing RBS AI chat model: {} at {}", chatModelName, rbsAiBaseUrl);

        return OpenAiChatModel.builder()
                .baseUrl(rbsAiBaseUrl)
                .apiKey(rbsAiApiKey)
                .modelName(chatModelName)
                .temperature(temperature)
                .timeout(chatTimeout)
                .maxRetries(1)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * Long-timeout RBS AI chat model for report generation.
     *
     * Report generation assembles large data blocks and asks the LLM to produce
     * a full markdown report — 300s (5 min) prevents premature timeouts.
     */
    @Bean
    @Qualifier("reportChatLanguageModel")
    public ChatLanguageModel reportChatLanguageModel() {
        log.info("Initializing RBS AI report chat model: {} at {} (timeout={})",
                reportModelName, rbsAiBaseUrl, reportTimeout);

        return OpenAiChatModel.builder()
                .baseUrl(rbsAiBaseUrl)
                .apiKey(rbsAiApiKey)
                .modelName(reportModelName)
                .temperature(reportTemperature)
                .timeout(reportTimeout)
                .maxRetries(1)
                .build();
    }

    // Embedding model — uncomment when RBS AI exposes an embeddings endpoint
    // @Bean
    // public EmbeddingModel embeddingModel() {
    //     log.info("Initializing RBS AI embedding model: {} at {}", embeddingModelName, rbsAiBaseUrl);
    //     return OpenAiEmbeddingModel.builder()
    //             .baseUrl(rbsAiBaseUrl)
    //             .apiKey(rbsAiApiKey)
    //             .modelName(embeddingModelName)
    //             .timeout(embeddingTimeout)
    //             .build();
    // }
}
