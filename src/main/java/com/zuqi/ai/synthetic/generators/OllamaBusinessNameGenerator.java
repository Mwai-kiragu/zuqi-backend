package com.zuqi.ai.synthetic.generators;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * LLM-powered business name generator (backed by RBS AI / Qwen3 14B).
 *
 * Calls the configured {@code ChatLanguageModel} (RBS AI) to produce
 * authentic Kenyan business names in batches. Results are cached in-memory
 * by category — a single LLM call covers the entire generation run.
 *
 * Degrades gracefully: if the LLM is unavailable or returns too few names,
 * the remaining quota is filled by {@link FallbackBusinessNameGenerator}.
 *
 * Marked {@link Primary} so Spring injects this implementation into
 * {@link MerchantProfileGenerator} in production; tests inject a mock or
 * the fallback directly.
 */
@Primary
@Component
@Slf4j
public class OllamaBusinessNameGenerator implements BusinessNameGenerator {

    /** Minimum names from the LLM before the fallback supplements. */
    private static final int MIN_ACCEPTABLE = 20;

    private final ChatLanguageModel chatLanguageModel;
    private final FallbackBusinessNameGenerator fallback;

    /** Cache keyed by category; populated lazily on first request for each category. */
    private final Map<String, List<String>> nameCache = new ConcurrentHashMap<>();

    public OllamaBusinessNameGenerator(ChatLanguageModel chatLanguageModel,
                                       FallbackBusinessNameGenerator fallback) {
        this.chatLanguageModel = chatLanguageModel;
        this.fallback          = fallback;
    }

    // -------------------------------------------------------------------------
    // BusinessNameGenerator
    // -------------------------------------------------------------------------

    @Override
    public List<String> generateBatch(String businessCategory, int count, long seed) {
        // Request a pool larger than needed so repeated calls don't run dry
        int poolSize = Math.max(count + 50, 100);
        List<String> pool = nameCache.computeIfAbsent(
                businessCategory,
                cat -> buildPool(cat, poolSize, seed)
        );

        // Supplement from fallback if pool is still too small
        if (pool.size() < count) {
            List<String> extra = fallback.generateBatch(businessCategory, count - pool.size(), seed);
            List<String> combined = new ArrayList<>(pool);
            combined.addAll(extra);
            pool = combined;
        }

        // Return a deterministic slice (cycling if necessary)
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(pool.get(i % pool.size()));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<String> buildPool(String category, int requestCount, long seed) {
        List<String> fromLlm = queryLlm(category, requestCount);
        if (fromLlm.size() >= MIN_ACCEPTABLE) {
            return fromLlm;
        }
        // Supplement with fallback names
        log.warn("LLM returned {} names for '{}', supplementing with fallback",
                fromLlm.size(), category);
        List<String> combined = new ArrayList<>(fromLlm);
        combined.addAll(fallback.generateBatch(category, requestCount - fromLlm.size(), seed));
        return combined;
    }

    private List<String> queryLlm(String category, int count) {
        String prompt = buildPrompt(category, count);
        try {
            log.info("Requesting {} business names from RBS AI for category '{}'", count, category);
            String response = chatLanguageModel.generate(prompt);
            List<String> names = parseResponse(response);
            log.info("RBS AI returned {} valid names for category '{}'", names.size(), category);
            return names;
        } catch (Exception e) {
            log.warn("LLM name generation failed for '{}': {}", category, e.getMessage());
            return List.of();
        }
    }

    private String buildPrompt(String category, int count) {
        String description = switch (category) {
            case "wholesale"   -> "wholesale goods supplier or bulk trader";
            case "distributor" -> "product distributor or distribution company";
            default            -> "small retail shop or convenience store";
        };
        return String.format(
            "Generate %d realistic Kenyan business names for a %s. " +
            "Use authentic Kenyan names from any ethnic group (Kikuyu, Luo, Luhya, Kalenjin, " +
            "Swahili, Somali, etc.). Include a mix of: owner surname + business type, " +
            "aspirational words, and location-inspired names. " +
            "Return ONLY the business names, one per line. No numbers, no explanations.",
            count, description
        );
    }

    /**
     * Parse LLM response into a clean list of business names.
     * Strips numbered list markers, blank lines, and names that are too short or too long.
     */
    private List<String> parseResponse(String response) {
        return Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.matches("^\\d+[.)\\-].*"))   // remove "1. Name" style
                .filter(line -> line.length() >= 4 && line.length() <= 70)
                .distinct()
                .collect(Collectors.toList());
    }
}
