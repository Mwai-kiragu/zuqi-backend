package com.zuqi.ai.pricing;

import dev.langchain4j.data.message.AiMessage;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PricingReasoningService.
 * Mocks ChatLanguageModel.generate(List<ChatMessage>) — no Ollama required.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PricingReasoningServiceTest {

    @Mock
    ChatLanguageModel chatLanguageModel;

    PricingReasoningService reasoningService;

    @BeforeEach
    void setUp() {
        reasoningService = AiServices.builder(PricingReasoningService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    private void stubLlmResponse(String text) {
        when(chatLanguageModel.generate(anyList()))
                .thenReturn(Response.from(AiMessage.from(text)));
    }

    @Test
    void generateReason_validContext_returnsExplanation() {
        String expected = "Demand elasticity is low — a 5% price increase reduces demand by " +
                "only 2% but increases weekly revenue by KES 8,500.";
        stubLlmResponse(expected);

        String result = reasoningService.generateReason(buildContext());

        assertThat(result).isEqualTo(expected);
        verify(chatLanguageModel).generate(anyList());
    }

    @Test
    void generateReason_contextPassedToModel() {
        stubLlmResponse("Some explanation");

        String result = reasoningService.generateReason("Product: Unga Flour\nCurrent price: KES 500");

        assertThat(result).isEqualTo("Some explanation");
        verify(chatLanguageModel).generate(anyList());
    }

    @Test
    void generateReason_systemMessageContainsPricingInstructions() {
        stubLlmResponse("Explanation");

        String result = reasoningService.generateReason("any context");

        assertThat(result).isEqualTo("Explanation");
        verify(chatLanguageModel).generate(anyList());
    }

    @Test
    void generateReason_decreasePriceContext_returnsExplanation() {
        String expected = "Reducing price by 10% is projected to increase demand by 25%, " +
                "growing weekly revenue by KES 15,000 — the product is price-sensitive.";
        stubLlmResponse(expected);

        String result = reasoningService.generateReason(buildDecreaseContext());

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void generateReason_calledOnce_perInvocation() {
        stubLlmResponse("Result");

        reasoningService.generateReason(buildContext());

        verify(chatLanguageModel).generate(anyList());
    }

    private String buildContext() {
        return """
                Product: Brookside Milk 500ml
                Current price: KES 60
                Recommended price: KES 63 (5.0% increase)
                Market average price for similar products: KES 62
                Predicted demand at current price: 200.0 units/week
                Predicted demand at recommended price: 196.0 units/week (-2.0% change)
                Estimated weekly revenue impact: KES +588
                """;
    }

    private String buildDecreaseContext() {
        return """
                Product: Premium Sugar 2kg
                Current price: KES 240
                Recommended price: KES 216 (10.0% decrease)
                Market average price for similar products: KES 230
                Predicted demand at current price: 150.0 units/week
                Predicted demand at recommended price: 187.5 units/week (+25.0% change)
                Estimated weekly revenue impact: KES +4050
                """;
    }
}
