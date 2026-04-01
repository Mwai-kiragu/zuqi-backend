package com.zuqi.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify RBS AI connectivity.
 *
 * Requires RBS_AI_API_KEY to be set in the environment.
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.2
 */
@SpringBootTest
@ActiveProfiles("test")
class RbsAiConnectivityTest {

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    // EmbeddingModel disabled until RBS AI exposes an embeddings endpoint
    // @Autowired
    // private EmbeddingModel embeddingModel;

    @Test
    void shouldConnectToChatModel() {
        String prompt = "Say 'RBS AI connected successfully' in exactly those words.";

        String response = chatLanguageModel.generate(prompt);

        assertThat(response).isNotNull();
        assertThat(response).isNotEmpty();
        System.out.println("Chat model response: " + response);
    }

    // Embedding test disabled until RBS AI exposes an embeddings endpoint
    // @Test
    // void shouldGenerateEmbeddings() {
    //     String text = "Merchant with excellent payment history and consistent orders";
    //     dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(text).content();
    //     assertThat(embedding).isNotNull();
    //     assertThat(embedding.dimension()).isEqualTo(1536);
    //     assertThat(embedding.vectorAsList()).hasSize(1536);
    //     System.out.println("Embedding dimension: " + embedding.dimension());
    // }
}
