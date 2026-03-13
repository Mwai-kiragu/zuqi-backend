package com.zuqi.ai.event.handler;

import com.zuqi.ai.credit.CreditScoringOrchestrator;
import com.zuqi.ai.event.MerchantCreatedEvent;
import com.zuqi.ai.service.MerchantEmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for MerchantCreditEventHandler.
 *
 * Verifies that both trigger paths (embedding + credit evaluation) run on
 * MerchantCreatedEvent, and that neither failure breaks the other step or
 * propagates an exception (non-blocking contract).
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.7
 */
@ExtendWith(MockitoExtension.class)
class MerchantCreditEventHandlerTest {

    @Mock
    private CreditScoringOrchestrator creditScoringOrchestrator;

    @Mock
    private MerchantEmbeddingService merchantEmbeddingService;

    @InjectMocks
    private MerchantCreditEventHandler handler;

    private static final UUID MERCHANT_ID   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID DISTRIBUTOR_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private MerchantCreatedEvent event() {
        return new MerchantCreatedEvent(
                MERCHANT_ID, DISTRIBUTOR_ID, "Kamau General Store",
                "+254700000001", "Nairobi", 1L, UUID.randomUUID(), LocalDateTime.now()
        );
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void handleMerchantCreated_shouldCallEmbedAndEvaluateInOrder() {
        handler.handleMerchantCreated(event());

        // Embedding must run before credit evaluation
        var inOrder = inOrder(merchantEmbeddingService, creditScoringOrchestrator);
        inOrder.verify(merchantEmbeddingService).embedMerchant(MERCHANT_ID);
        inOrder.verify(creditScoringOrchestrator).evaluateMerchant(MERCHANT_ID);
    }

    // -------------------------------------------------------------------------
    // Graceful degradation — individual step failures must not propagate
    // -------------------------------------------------------------------------

    @Test
    void handleMerchantCreated_shouldNotThrow_whenEmbeddingFails() {
        doThrow(new RuntimeException("Ollama unreachable"))
                .when(merchantEmbeddingService).embedMerchant(MERCHANT_ID);

        // Must complete without throwing
        handler.handleMerchantCreated(event());

        // Embedding was attempted
        verify(merchantEmbeddingService).embedMerchant(MERCHANT_ID);
        // Credit evaluation is skipped after the exception — the catch block fires
        verifyNoInteractions(creditScoringOrchestrator);
    }

    @Test
    void handleMerchantCreated_shouldNotThrow_whenCreditEvaluationFails() {
        doThrow(new RuntimeException("LLM timeout"))
                .when(creditScoringOrchestrator).evaluateMerchant(MERCHANT_ID);

        // Must complete without throwing
        handler.handleMerchantCreated(event());

        verify(merchantEmbeddingService).embedMerchant(MERCHANT_ID);
        verify(creditScoringOrchestrator).evaluateMerchant(MERCHANT_ID);
    }

    @Test
    void handleMerchantCreated_shouldNotThrow_whenBothStepsFail() {
        doThrow(new RuntimeException("DB down")).when(merchantEmbeddingService).embedMerchant(any());
        // Second step won't be reached, but handler should still be silent

        handler.handleMerchantCreated(event());

        verifyNoInteractions(creditScoringOrchestrator);
    }
}
