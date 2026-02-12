package com.zuqi.ai.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Prometheus metrics service for LLM operations.
 *
 * Tracks:
 * - Request counts by provider/model/module
 * - Latency histograms
 * - Error rates
 *
 * Blueprint reference: implementation_plan.md Phase 2 Task 2.8
 */
@Service
@Slf4j
public class LlmMetricsService {

    private final MeterRegistry meterRegistry;

    public LlmMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record a successful LLM request with latency.
     *
     * @param provider LLM provider (e.g., "ollama", "openai")
     * @param model Model name (e.g., "qwen2.5:32b")
     * @param module AI module (e.g., "credit_scoring")
     * @param durationMs Request duration in milliseconds
     */
    public void recordRequest(String provider, String model, String module, long durationMs) {
        // Increment request counter
        Counter.builder("zuqi.ai.llm.requests.total")
                .description("Total number of LLM requests")
                .tag("provider", provider)
                .tag("model", model)
                .tag("module", module)
                .tag("status", "success")
                .register(meterRegistry)
                .increment();

        // Record latency
        Timer.builder("zuqi.ai.llm.latency.seconds")
                .description("LLM request latency")
                .tag("provider", provider)
                .tag("model", model)
                .tag("module", module)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));

        log.debug("Recorded LLM metrics: provider={}, model={}, module={}, duration={}ms",
                provider, model, module, durationMs);
    }

    /**
     * Record an LLM error.
     *
     * @param provider LLM provider
     * @param model Model name
     * @param module AI module
     * @param errorType Error type (e.g., "timeout", "connection_error", "invalid_response")
     */
    public void recordError(String provider, String model, String module, String errorType) {
        Counter.builder("zuqi.ai.llm.errors.total")
                .description("Total number of LLM errors")
                .tag("provider", provider)
                .tag("model", model)
                .tag("module", module)
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();

        log.warn("Recorded LLM error: provider={}, model={}, module={}, error={}",
                provider, model, module, errorType);
    }

    /**
     * Execute a callable and automatically track metrics.
     *
     * @param provider LLM provider
     * @param model Model name
     * @param module AI module
     * @param operation Operation to execute
     * @return Result of the operation
     * @throws Exception if operation fails
     */
    public <T> T recordOperation(String provider, String model, String module, Callable<T> operation) throws Exception {
        long startTime = System.currentTimeMillis();

        try {
            T result = operation.call();
            long duration = System.currentTimeMillis() - startTime;
            recordRequest(provider, model, module, duration);
            return result;

        } catch (Exception e) {
            String errorType = determineErrorType(e);
            recordError(provider, model, module, errorType);
            throw e;
        }
    }

    /**
     * Determine error type from exception.
     */
    private String determineErrorType(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (message.contains("timeout")) {
            return "timeout";
        } else if (message.contains("connection")) {
            return "connection_error";
        } else if (message.contains("invalid") || message.contains("parse")) {
            return "invalid_response";
        } else if (message.contains("rate limit")) {
            return "rate_limit";
        } else {
            return "unknown";
        }
    }
}
