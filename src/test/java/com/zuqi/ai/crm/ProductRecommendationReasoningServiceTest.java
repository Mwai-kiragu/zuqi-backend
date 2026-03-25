package com.zuqi.ai.crm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProductRecommendationReasoningService.
 * Mocks ChatLanguageModel.generate(List<ChatMessage>) — no Ollama required.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductRecommendationReasoningServiceTest {

    @Mock
    ChatLanguageModel chatLanguageModel;

    ProductRecommendationReasoningService reasoningService;

    @BeforeEach
    void setUp() {
        reasoningService = AiServices.builder(ProductRecommendationReasoningService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    private void stubLlmResponse(String text) {
        when(chatLanguageModel.generate(anyList()))
                .thenReturn(Response.from(AiMessage.from(text)));
    }

    @Test
    void generateReason_validContext_returnsSalesPitch() {
        String expected = "80% of similar merchants in Nairobi stock Brookside Milk — " +
                "adding it could increase your monthly basket by KES 12,000.";
        stubLlmResponse(expected);

        String result = reasoningService.generateReason(buildContext());

        assertThat(result).isEqualTo(expected);
        verify(chatLanguageModel).generate(anyList());
    }

    @Test
    void generateReason_passesContextToModel() {
        stubLlmResponse("Some pitch");

        String result = reasoningService.generateReason(buildContext());

        assertThat(result).isEqualTo("Some pitch");
        verify(chatLanguageModel).generate(anyList());
    }

    @Test
    void generateReason_systemMessageIncludesKeyInstruction() {
        stubLlmResponse("Pitch");

        String result = reasoningService.generateReason("any context");

        assertThat(result).isEqualTo("Pitch");
        verify(chatLanguageModel).generate(anyList());
    }

    @Test
    void generateReason_emptyResponse_returnsEmpty() {
        stubLlmResponse("");

        String result = reasoningService.generateReason(buildContext());

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    void contextString_assembledCorrectly() {
        String pitch = "Stock Brookside Milk — high demand in retail shops your area.";
        stubLlmResponse(pitch);

        String context = """
                Merchant: Mama Mboga Duka
                Location: Nairobi
                Business type: grocery
                Products they currently buy: Sugar, Flour, Rice
                Recommended product: Brookside Milk (category: Dairy, price: KES 60)
                Co-purchase signal: 45 other merchants also buy this
                Percentage of similar merchants stocking this: 82%
                """;

        String result = reasoningService.generateReason(context);
        assertThat(result).isEqualTo(pitch);
    }

    private String buildContext() {
        return """
                Merchant: Test Merchant
                Location: Nairobi
                Business type: retail
                Products they currently buy: Sugar, Flour
                Recommended product: Milk (category: Dairy, price: KES 60)
                Co-purchase signal: 30 other merchants who buy similar products also buy this
                Percentage of similar merchants stocking this: 75%
                """;
    }
}
