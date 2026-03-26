package com.zuqi.ai.service;

import com.zuqi.ai.dto.AIModelListResponse;
import com.zuqi.ai.dto.AIModelPerformanceResponse;
import com.zuqi.ai.dto.AISystemHealthResponse;
import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.ai.synthetic.SyntheticGenerationService;
import com.zuqi.domain.ai.AISyntheticRun;
import com.zuqi.domain.ai.AIModelPerformance;
import com.zuqi.domain.ai.AIModelRegistry;
import com.zuqi.domain.ai.MetricName;
import com.zuqi.domain.ai.ModelStatus;
import com.zuqi.repository.AIModelPerformanceRepository;
import com.zuqi.repository.AIModelRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of AIHealthService for system health monitoring.
 *
 * Performs health checks on:
 * - Model registry (database connectivity, model counts)
 * - Feature services (cache status)
 * - LLM connectivity (placeholder for Phase 2)
 * - Cache infrastructure (Redis)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIHealthServiceImpl implements AIHealthService {

    private final AIModelRegistryRepository modelRegistryRepository;
    private final AIModelPerformanceRepository modelPerformanceRepository;
    private final CacheManager cacheManager;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ModelRegistry modelRegistry;
    private final SyntheticDataOrchestrator syntheticDataOrchestrator;
    private final SyntheticGenerationService syntheticGenerationService;

    @Value("${langchain4j.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String ollamaModelName;

    @Override
    public AISystemHealthResponse getSystemHealth() {
        log.debug("Performing AI system health check");

        // Check model registry
        AISystemHealthResponse.ModelRegistryHealth modelRegistryHealth = checkModelRegistry();

        // Check feature services
        AISystemHealthResponse.FeatureServicesHealth featureServicesHealth = checkFeatureServices();

        // Check LLM connectivity (placeholder for Phase 2)
        AISystemHealthResponse.LLMConnectivityHealth llmConnectivityHealth = checkLLMConnectivity();

        // Check cache
        AISystemHealthResponse.CacheHealth cacheHealth = checkCacheHealth();

        // Determine overall status
        String overallStatus = determineOverallStatus(
                modelRegistryHealth.status(),
                featureServicesHealth.status(),
                llmConnectivityHealth.status(),
                cacheHealth.status()
        );

        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("version", "1.0.0");
        additionalInfo.put("environment", "production");

        return AISystemHealthResponse.builder()
                .status(overallStatus)
                .timestamp(LocalDateTime.now())
                .modelRegistry(modelRegistryHealth)
                .featureServices(featureServicesHealth)
                .llmConnectivity(llmConnectivityHealth)
                .cache(cacheHealth)
                .additionalInfo(additionalInfo)
                .build();
    }

    @Override
    public AIModelListResponse getActiveModels() {
        log.debug("Retrieving active models from registry");

        List<AIModelRegistry> models = modelRegistryRepository.findByStatus(ModelStatus.ACTIVE);

        List<AIModelListResponse.ModelSummary> summaries = models.stream()
                .map(model -> {
                    String[] primary = primaryMetric(model.getModelName());
                    String metricKey  = primary[0];
                    String metricName = primary[1];
                    Double metricValue = extractMetric(model, metricKey);
                    return AIModelListResponse.ModelSummary.builder()
                            .id(model.getId())
                            .modelName(model.getModelName())
                            .version(model.getModelVersion().toString())
                            .status(model.getStatus())
                            .modelType(model.getAlgorithm())
                            .accuracy(extractMetric(model, "accuracy"))
                            .primaryMetricName(metricName)
                            .primaryMetricValue(metricValue)
                            .trainedAt(model.getCreatedAt())
                            .promotedAt(model.getPromotedAt())
                            .build();
                })
                .toList();

        return AIModelListResponse.builder()
                .models(summaries)
                .totalCount(summaries.size())
                .retrievedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public AIModelPerformanceResponse getModelPerformance(String modelName) {
        log.debug("Retrieving performance metrics for model: {}", modelName);

        // Get the active version of the model
        AIModelRegistry model = modelRegistryRepository.findByModelNameAndStatus(modelName, ModelStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("No active model found with name: " + modelName));

        // Build current metrics from model registry's performance_metrics JSON field
        AIModelPerformanceResponse.PerformanceMetrics currentMetrics = AIModelPerformanceResponse.PerformanceMetrics.builder()
                .accuracy(extractMetric(model, "accuracy"))
                .precision(extractMetric(model, "precision"))
                .recall(extractMetric(model, "recall"))
                .f1Score(extractMetric(model, "f1_score"))
                .mae(extractMetric(model, "mae"))
                .rmse(extractMetric(model, "rmse"))
                .customMetrics(model.getPerformanceMetrics() != null ? model.getPerformanceMetrics() : new HashMap<>())
                .build();

        // Build performance history from ai_model_performance table.
        // Group rows by evaluation_date, then extract accuracy/precision/recall/f1 per snapshot.
        List<LocalDate> evaluationDates = modelPerformanceRepository
                .findDistinctEvaluationDates(modelName);

        List<AIModelPerformanceResponse.PerformanceHistory> history = evaluationDates.stream()
                .limit(30) // cap at 30 snapshots for response size
                .map(date -> {
                    List<AIModelPerformance> metrics = modelPerformanceRepository
                            .findByModelNameAndModelVersionAndEvaluationDate(
                                    modelName, model.getModelVersion(), date);

                    Map<MetricName, Double> metricMap = metrics.stream()
                            .collect(Collectors.toMap(
                                    AIModelPerformance::getMetricName,
                                    AIModelPerformance::getMetricValue,
                                    (a, b) -> a));

                    return AIModelPerformanceResponse.PerformanceHistory.builder()
                            .recordedAt(date.atStartOfDay())
                            .accuracy(metricMap.get(MetricName.ACCURACY))
                            .precision(metricMap.get(MetricName.PRECISION))
                            .recall(metricMap.get(MetricName.RECALL))
                            .f1Score(metricMap.get(MetricName.F1))
                            .build();
                })
                .collect(Collectors.toList());

        return AIModelPerformanceResponse.builder()
                .modelName(modelName)
                .version(model.getModelVersion().toString())
                .status(model.getStatus().name())
                .currentMetrics(currentMetrics)
                .history(history)
                .retrievedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public void retireModel(String modelName) {
        log.info("Retiring active model: {}", modelName);
        AIModelRegistry active = modelRegistryRepository.findByModelNameAndStatus(modelName, ModelStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("No active model: " + modelName));
        modelRegistry.retireModel(active.getId());
        log.info("Model {} (id={}) retired successfully", modelName, active.getId());
    }

    @Override
    public void triggerRetrainAll() {
        log.info("Retrain-all requested — launching async synthetic generation + model training");
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(null, 42L);
        AISyntheticRun run = syntheticDataOrchestrator.createRunRecord(null, config, "retrain-all");
        syntheticGenerationService.generateAsync(run.getId(), config);
        log.info("Retrain-all: async run {} enqueued (merchants={}, months={}, seed={})",
                run.getId(), config.merchantCount(), config.historyMonths(), config.randomSeed());
    }

    @Override
    public void triggerRetrainModel(String modelName) {
        log.info("Retrain requested for model '{}'", modelName);
        SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(null, 42L);
        AISyntheticRun run = syntheticDataOrchestrator.createRunRecord(null, config, "retrain-model:" + modelName);
        syntheticGenerationService.generateAsync(run.getId(), config, java.util.Set.of(modelName));
        log.info("Retrain-model '{}': async run {} enqueued", modelName, run.getId());
    }

    /**
     * Extract a metric value from the model's performanceMetrics JSON field.
     */
    private Double extractMetric(AIModelRegistry model, String metricName) {
        if (model.getPerformanceMetrics() == null) {
            return null;
        }
        Object value = model.getPerformanceMetrics().get(metricName);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    /**
     * Returns [metricKey, displayName] for the primary quality metric of each model.
     * metricKey matches what the training pipeline stores in performance_metrics JSON.
     */
    private String[] primaryMetric(String modelName) {
        return switch (modelName) {
            case "credit_classifier",
                 "stockout_predictor",
                 "payment_distress_classifier",
                 "data_quality_detector",
                 "data_quality_classifier",
                 "churn_predictor",
                 "bank_recon_matcher",
                 "expiry_risk_predictor"     -> new String[]{"auc_roc",  "AUC-ROC"};
            case "shrinkage_detector",
                 "payment_anomaly_detector"  -> new String[]{"f1",       "F1"};
            case "cash_flow_predictor"       -> new String[]{"r2",       "R²"};
            case "demand_forecaster",
                 "credit_limit_regressor",
                 "rep_performance_predictor",
                 "customer_clv_predictor",
                 "visit_optimizer",
                 "smart_pricing_recommender" -> new String[]{"rmse",     "RMSE"};
            default                          -> new String[]{"accuracy", "Accuracy"};
        };
    }

    // ==================== Helper Methods ====================

    private AISystemHealthResponse.ModelRegistryHealth checkModelRegistry() {
        try {
            long totalModels = modelRegistryRepository.count();
            long activeModels = modelRegistryRepository.countByStatus(ModelStatus.ACTIVE);

            return AISystemHealthResponse.ModelRegistryHealth.builder()
                    .status("UP")
                    .totalModels((int) totalModels)
                    .activeModels((int) activeModels)
                    .databaseConnection("CONNECTED")
                    .build();
        } catch (Exception e) {
            log.error("Model registry health check failed", e);
            return AISystemHealthResponse.ModelRegistryHealth.builder()
                    .status("DOWN")
                    .totalModels(0)
                    .activeModels(0)
                    .databaseConnection("ERROR: " + e.getMessage())
                    .build();
        }
    }

    private AISystemHealthResponse.FeatureServicesHealth checkFeatureServices() {
        List<AISystemHealthResponse.FeatureServiceStatus> services = new ArrayList<>();

        // Check each feature service's cache
        String[] featureServices = {
                "merchantFeatures",
                "orderFeatures",
                "paymentFeatures",
                "inventoryFeatures",
                "salesRepFeatures"
        };

        for (String serviceName : featureServices) {
            String cacheStatus = "UNKNOWN";
            try {
                if (cacheManager.getCache(serviceName) != null) {
                    cacheStatus = "AVAILABLE";
                }
            } catch (Exception e) {
                cacheStatus = "ERROR";
                log.error("Failed to check cache for {}", serviceName, e);
            }

            services.add(AISystemHealthResponse.FeatureServiceStatus.builder()
                    .serviceName(serviceName)
                    .status("UP")
                    .cacheStatus(cacheStatus)
                    .build());
        }

        return AISystemHealthResponse.FeatureServicesHealth.builder()
                .status("UP")
                .services(services)
                .build();
    }

    private AISystemHealthResponse.LLMConnectivityHealth checkLLMConnectivity() {
        AISystemHealthResponse.OllamaStatus ollamaStatus = pingOllama();

        AISystemHealthResponse.CloudLLMStatus cloudStatus = AISystemHealthResponse.CloudLLMStatus.builder()
                .status("NOT_CONFIGURED")
                .provider("OpenAI/Anthropic")
                .message("Cloud LLM fallback not in use — local Ollama is primary")
                .build();

        String overallStatus = "UP".equals(ollamaStatus.status()) ? "UP" : "DOWN";
        return AISystemHealthResponse.LLMConnectivityHealth.builder()
                .status(overallStatus)
                .ollama(ollamaStatus)
                .cloudLLM(cloudStatus)
                .build();
    }

    /**
     * Pings the Ollama /api/tags endpoint to verify connectivity and confirm
     * the configured model is available on the server.
     */
    private AISystemHealthResponse.OllamaStatus pingOllama() {
        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(5000);
            RestTemplate restTemplate = new RestTemplate(factory);

            ResponseEntity<String> response = restTemplate.getForEntity(
                    ollamaBaseUrl + "/api/tags", String.class);

            String body = response.getBody();
            if (response.getStatusCode() == HttpStatus.OK && body != null) {
                boolean modelPresent = body.contains("\"" + ollamaModelName + "\"")
                        || body.contains(ollamaModelName.replace(":", "\":\""));

                if (modelPresent) {
                    log.debug("Ollama health check: UP, model {} confirmed available", ollamaModelName);
                    return AISystemHealthResponse.OllamaStatus.builder()
                            .status("UP")
                            .baseUrl(ollamaBaseUrl)
                            .model(ollamaModelName)
                            .message("Ollama reachable and model " + ollamaModelName + " is available")
                            .build();
                } else {
                    log.warn("Ollama health check: reachable but model {} not found in tag list", ollamaModelName);
                    return AISystemHealthResponse.OllamaStatus.builder()
                            .status("DEGRADED")
                            .baseUrl(ollamaBaseUrl)
                            .model(ollamaModelName)
                            .message("Ollama reachable but model '" + ollamaModelName + "' not found — check OLLAMA_MODEL config")
                            .build();
                }
            } else {
                return AISystemHealthResponse.OllamaStatus.builder()
                        .status("DOWN")
                        .baseUrl(ollamaBaseUrl)
                        .model(ollamaModelName)
                        .message("Ollama returned unexpected status: " + response.getStatusCode())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Ollama health check failed: {}", e.getMessage());
            return AISystemHealthResponse.OllamaStatus.builder()
                    .status("DOWN")
                    .baseUrl(ollamaBaseUrl)
                    .model(ollamaModelName)
                    .message("Cannot reach Ollama at " + ollamaBaseUrl + " — " + e.getMessage())
                    .build();
        }
    }

    private AISystemHealthResponse.CacheHealth checkCacheHealth() {
        try {
            // Ping Redis
            redisConnectionFactory.getConnection().ping();

            return AISystemHealthResponse.CacheHealth.builder()
                    .status("UP")
                    .provider("REDIS")
                    .connection("CONNECTED")
                    .build();
        } catch (Exception e) {
            log.error("Cache health check failed", e);
            return AISystemHealthResponse.CacheHealth.builder()
                    .status("DOWN")
                    .provider("REDIS")
                    .connection("ERROR: " + e.getMessage())
                    .build();
        }
    }

    private String determineOverallStatus(String... componentStatuses) {
        boolean anyDown = false;
        boolean anyDegraded = false;

        for (String status : componentStatuses) {
            if ("DOWN".equals(status)) {
                anyDown = true;
            } else if ("DEGRADED".equals(status) || "NOT_CONFIGURED".equals(status)) {
                anyDegraded = true;
            }
        }

        if (anyDown) {
            return "DOWN";
        } else if (anyDegraded) {
            return "DEGRADED";
        } else {
            return "UP";
        }
    }
}
