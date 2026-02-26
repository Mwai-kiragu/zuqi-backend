package com.zuqi.ai.synthetic.generators;

import java.util.List;

/**
 * Contract for generating realistic Kenyan business names for synthetic merchants.
 *
 * Implementations must be deterministic: the same {@code seed} must draw from the
 * same name pool so that a given random seed reproduces the same dataset.
 *
 * <p>Two implementations are provided:
 * <ul>
 *   <li>{@link OllamaBusinessNameGenerator} (primary) — calls the local Ollama LLM
 *       for authentic-sounding names; degrades gracefully to the fallback on failure.</li>
 *   <li>{@link FallbackBusinessNameGenerator} — template-based combinations of common
 *       Kenyan names and business suffixes; used in tests and when Ollama is offline.</li>
 * </ul>
 */
public interface BusinessNameGenerator {

    /**
     * Generate a list of business names appropriate for the given category.
     *
     * @param businessCategory one of {@code "retail"}, {@code "wholesale"}, or {@code "distributor"}
     * @param count            how many names to return
     * @param seed             determinism hint — same seed should draw from the same pool
     * @return list of {@code count} business names (may contain duplicates when the
     *         requested count exceeds the available pool size)
     */
    List<String> generateBatch(String businessCategory, int count, long seed);
}
