package com.zuqi.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuqi.domain.ai.Recommendation;
import com.zuqi.domain.ai.RecommendationPriority;
import com.zuqi.domain.ai.RecommendationStatus;
import com.zuqi.domain.ai.RecommendationType;
import com.zuqi.domain.distributor.Distributor;
import com.zuqi.repository.DistributorRepository;
import com.zuqi.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service that orchestrates the AI recommendation generation workflow.
 *
 * Responsibilities:
 * 1. Build a distributor context string for the LLM prompt
 * 2. Invoke RecommendationAgent (tool-calling LLM) to generate raw JSON
 * 3. Parse the returned JSON array into Recommendation domain entities
 * 4. Persist all recommendations in a single batch save
 *
 * Blueprint reference: implementation_plan.md Phase 6 Task 6.2
 */
@SuppressWarnings("DataFlowIssue")  // IDE false-positives on Mockito/JPA @NonNull parameters
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final RecommendationAgent recommendationAgent;
    private final RecommendationRepository recommendationRepository;
    private final DistributorRepository distributorRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generate AI-powered recommendations for a distributor and persist them.
     *
     * @param distributorId target distributor UUID
     * @return list of saved Recommendation entities (empty on LLM failure)
     * @throws IllegalArgumentException if the distributor does not exist
     */
    @Transactional
    public List<Recommendation> generateAndSave(UUID distributorId) {
        // 1. Load distributor — fail fast if not found
        Distributor distributor = distributorRepository.findById(distributorId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Distributor not found: " + distributorId));

        // 2. Build context string for the LLM prompt
        String context = "Distributor ID: " + distributorId
                + ", Analysis date: " + LocalDate.now();

        log.info("Generating recommendations for distributor {} ({})",
                distributor.getName(), distributorId);

        // 3. Call the LLM agent — gracefully degrade on any LLM failure
        String llmResponse;
        try {
            llmResponse = recommendationAgent.generateRecommendations(context);
        } catch (Exception e) {
            log.error("LLM agent failed to generate recommendations for distributor {}: {}",
                    distributorId, e.getMessage(), e);
            return Collections.emptyList();
        }

        if (llmResponse == null || llmResponse.isBlank()) {
            log.warn("LLM returned blank response for distributor {}", distributorId);
            return Collections.emptyList();
        }

        // 4. Parse the JSON array from the response
        List<Map<String, Object>> rawRecommendations;
        try {
            rawRecommendations = extractAndParseJsonArray(llmResponse);
        } catch (Exception e) {
            log.error("Failed to parse LLM response for distributor {}: {}",
                    distributorId, e.getMessage());
            log.debug("Raw LLM response was: {}", llmResponse);
            return Collections.emptyList();
        }

        if (rawRecommendations.isEmpty()) {
            log.warn("LLM response contained no parseable recommendations for distributor {}",
                    distributorId);
            return Collections.emptyList();
        }

        // 5. Map raw maps → Recommendation entities
        List<Recommendation> entities = rawRecommendations.stream()
                .map(map -> mapToRecommendation(map, distributor))
                .collect(Collectors.toList());

        // 6. Batch save
        List<Recommendation> saved = recommendationRepository.saveAll(entities);
        log.info("Persisted {} recommendations for distributor {}", saved.size(), distributorId);
        return saved;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Extract the JSON array block from the LLM's markdown-fenced response and
     * deserialise it into a list of raw maps.
     *
     * Accepts three formats:
     *   - ```json\n[ ... ]\n```
     *   - ```\n[ ... ]\n```
     *   - bare JSON array [ ... ]
     */
    private List<Map<String, Object>> extractAndParseJsonArray(String response) throws Exception {
        String json = response.trim();

        // Prefer the fenced ```json ... ``` block
        int jsonFenceStart = json.indexOf("```json");
        if (jsonFenceStart != -1) {
            int contentStart = json.indexOf('\n', jsonFenceStart);
            int fenceEnd = json.indexOf("```", contentStart);
            if (contentStart != -1 && fenceEnd != -1) {
                json = json.substring(contentStart, fenceEnd).trim();
            }
        } else {
            // Fall back to a plain ``` ... ``` block
            int fenceStart = json.indexOf("```");
            if (fenceStart != -1) {
                int contentStart = json.indexOf('\n', fenceStart);
                int fenceEnd = json.indexOf("```", contentStart);
                if (contentStart != -1 && fenceEnd != -1) {
                    json = json.substring(contentStart, fenceEnd).trim();
                }
            }
        }

        // Strip any leading/trailing whitespace one more time after extraction
        json = json.trim();

        return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * Convert a raw map (from the LLM JSON) into a Recommendation entity.
     *
     * Applies safe defaults for every field so that a partially-formed map
     * never causes a NullPointerException or constraint violation.
     */
    private Recommendation mapToRecommendation(Map<String, Object> map, Distributor distributor) {
        // recommendationType — default to SALES_TREND
        RecommendationType recommendationType = RecommendationType.SALES_TREND;
        Object typeObj = map.get("recommendationType");
        if (typeObj != null) {
            try {
                recommendationType = RecommendationType.valueOf(typeObj.toString().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown recommendationType '{}', defaulting to SALES_TREND", typeObj);
            }
        }

        // priority — default to MEDIUM
        RecommendationPriority priority = RecommendationPriority.MEDIUM;
        Object priorityObj = map.get("priority");
        if (priorityObj != null) {
            try {
                priority = RecommendationPriority.valueOf(priorityObj.toString().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown priority '{}', defaulting to MEDIUM", priorityObj);
            }
        }

        // observation — required TEXT field
        String observation = getString(map, "observation", "No observation provided");

        // recommendation — required TEXT field
        String recommendation = getString(map, "recommendation", "No recommendation provided");

        // expectedImpact — optional
        String expectedImpact = getString(map, "expectedImpact", null);

        // evidence — flexible: accept Map or stringify a non-map value
        Map<String, Object> evidence = buildEvidenceMap(map.get("evidence"));

        return Recommendation.builder()
                .distributor(distributor)
                .recommendationType(recommendationType)
                .observation(observation)
                .evidence(evidence)
                .recommendation(recommendation)
                .expectedImpact(expectedImpact)
                .priority(priority)
                .status(RecommendationStatus.PENDING)
                .build();
    }

    /**
     * Safely extract a String value from the map, returning {@code defaultValue} when absent.
     */
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        String str = value.toString().trim();
        return str.isEmpty() ? defaultValue : str;
    }

    /**
     * Convert the "evidence" field to a {@code Map<String,Object>}.
     *
     * The LLM may return evidence as:
     *   - a proper JSON object  → use it directly
     *   - a plain string        → wrap under key "summary"
     *   - null / missing        → return an empty map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildEvidenceMap(Object evidenceRaw) {
        if (evidenceRaw == null) {
            return new HashMap<>();
        }
        if (evidenceRaw instanceof Map) {
            return (Map<String, Object>) evidenceRaw;
        }
        // Treat anything else as a plain string summary
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("summary", evidenceRaw.toString());
        return wrapper;
    }
}
