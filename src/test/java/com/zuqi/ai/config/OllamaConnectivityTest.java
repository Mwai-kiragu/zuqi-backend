package com.zuqi.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify Ollama connectivity.
 *
 * This test requires Ollama server to be running at http://192.168.2.17:11434
 * with the qwen2.5:32b and all-minilm-l6-v2 models available.
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.2
 */
@SpringBootTest
@ActiveProfiles("test")
class OllamaConnectivityTest {

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void shouldConnectToChatModel() {
        // Given a simple prompt
        String prompt = "Say 'Ollama connected successfully' in exactly those words.";

        // When calling the chat model
        String response = chatLanguageModel.generate(prompt);

        // Then we should get a valid response
        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        System.out.println("Chat model response: " + response);
    }

    @Test
    void shouldGenerateEmbeddings() {
        // Given a sample text
        String text = "Merchant with excellent payment history and consistent orders";

        // When generating embeddings
        dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(text).content();

        // Then we should get a 768-dimensional vector (nomic-embed-text)
        assertThat(embedding).isNotNull();
        assertThat(embedding.dimension()).isEqualTo(768);
        assertThat(embedding.vectorAsList()).hasSize(768);
        System.out.println("Embedding dimension: " + embedding.dimension());
    }
}
