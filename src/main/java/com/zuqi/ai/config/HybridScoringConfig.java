package com.zuqi.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Configuration for hybrid ML+LLM credit scoring.
 *
 * Binds properties from application.yml under zuqi.ai.credit-scoring.
 *
 * Blueprint: ML_IMPLEMENTATION_PLAN.md Task 6
 */
@Configuration
@ConfigurationProperties(prefix = "zuqi.ai.credit-scoring")
@Data
public class HybridScoringConfig {

    /**
     * Scoring mode.
     */
    private ScoringMode mode = ScoringMode.HYBRID;

    /**
     * Hybrid scoring weights.
     */
    private HybridWeights hybridWeights = new HybridWeights();

    /**
     * Score difference threshold for flagging discrepancies.
     */
    private int flagDiscrepancyThreshold = 15;

    /**
     * LLM validation triggers.
     */
    private LlmValidationTriggers llmValidationTriggers = new LlmValidationTriggers();

    /**
     * Scoring mode options.
     */
    public enum ScoringMode {
        LLM_ONLY,   // 100% LLM scoring (Phase 2)
        ML_ONLY,    // 100% ML scoring (Phase 5+)
        HYBRID      // ML primary + LLM validation (Phase 3-4)
    }

    /**
     * Hybrid scoring weights (must sum to 1.0).
     */
    @Data
    public static class HybridWeights {
        private double ml = 0.7;   // ML primary scorer
        private double llm = 0.3;  // LLM validator

        /**
         * Validate weights sum to 1.0.
         */
        public void validate() {
            double sum = ml + llm;
            if (Math.abs(sum - 1.0) > 0.01) {
                throw new IllegalStateException(
                        String.format("Hybrid weights must sum to 1.0 (got %.2f)", sum));
            }
        }
    }

    /**
     * Triggers for LLM validation in hybrid mode.
     */
    @Data
    public static class LlmValidationTriggers {
        /**
         * Credit limit threshold (KES) - validate if limit > this value.
         */
        private BigDecimal highValueLimit = BigDecimal.valueOf(500_000);

        /**
         * ML confidence threshold - validate if confidence < this value.
         */
        private double lowMlConfidence = 0.6;

        /**
         * Validate new merchant categories not in training data.
         */
        private boolean newCategoryValidation = true;

        /**
         * Random sample rate for LLM validation (0.0-1.0).
         */
        private double sampleRate = 0.3;
    }

    /**
     * Validate configuration on initialization.
     */
    public void validate() {
        hybridWeights.validate();

        if (flagDiscrepancyThreshold < 0 || flagDiscrepancyThreshold > 100) {
            throw new IllegalStateException(
                    "flagDiscrepancyThreshold must be between 0 and 100");
        }

        if (llmValidationTriggers.getSampleRate() < 0.0 ||
            llmValidationTriggers.getSampleRate() > 1.0) {
            throw new IllegalStateException(
                    "sampleRate must be between 0.0 and 1.0");
        }

        if (llmValidationTriggers.getLowMlConfidence() < 0.0 ||
            llmValidationTriggers.getLowMlConfidence() > 1.0) {
            throw new IllegalStateException(
                    "lowMlConfidence must be between 0.0 and 1.0");
        }
    }
}
