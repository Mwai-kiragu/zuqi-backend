package com.zuqi.ai.crm;

import com.zuqi.ai.model.ModelRegistry;
import com.zuqi.ai.pipeline.ModelEvaluator;
import com.zuqi.ai.synthetic.SyntheticDataBundle;
import com.zuqi.ai.synthetic.SyntheticDataConfig;
import com.zuqi.ai.synthetic.SyntheticDataOrchestrator;
import com.zuqi.ai.synthetic.dto.SyntheticMerchant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tribuo.Model;
import org.tribuo.MutableDataset;
import org.tribuo.Trainer;
import org.tribuo.clustering.ClusterID;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Training pipeline for customer segmentation (K-Means, 5 clusters).
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Generate 500 synthetic merchants via {@link SyntheticDataOrchestrator}.</li>
 *   <li>Compute {@link CustomerAnalyticsFeatures} for each merchant.</li>
 *   <li>Build a {@link MutableDataset} of unlabelled clustering examples.</li>
 *   <li>Train K-Means (all data — no train/test split for unsupervised).</li>
 *   <li>Predict clusters, sort by avg revenue, assign human-readable labels.</li>
 *   <li>Register model in registry and promote to ACTIVE.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentationTrainingPipeline {

    public static final String MODEL_NAME = "customer_segmenter";

    private static final String[] CLUSTER_LABELS = {
            "HIGH_VALUE_GROWING",
            "STABLE_MID_TIER",
            "AT_RISK_DECLINING",
            "NEW_LOW_ACTIVITY",
            "HIGH_VALUE_AT_RISK"
    };

    private final SyntheticDataOrchestrator orchestrator;
    private final SyntheticCustomerAnalyticsFeatureBuilder featureBuilder;
    private final SegmentationFeatureBuilder segmentationFeatureBuilder;
    private final ModelRegistry modelRegistry;
    private final ModelEvaluator modelEvaluator;
    private final Trainer<ClusterID> kMeansTrainer;

    @Transactional
    public TrainingResult runPipeline() {
        log.info("=== Starting Customer Segmentation Training Pipeline ===");
        long start = System.currentTimeMillis();

        try {
            // Step 1: Generate synthetic merchants
            SyntheticDataConfig config = SyntheticDataConfig.defaultConfig(null, 42L);
            SyntheticDataBundle bundle = orchestrator.generateBundle(config);
            List<SyntheticMerchant> merchants = bundle.getMerchants();
            log.info("Generated {} synthetic merchants", merchants.size());

            // Step 2: Compute features
            LocalDateTime asOf = LocalDateTime.now();
            List<CustomerAnalyticsFeatures> featuresList = new ArrayList<>();
            for (SyntheticMerchant m : merchants) {
                featuresList.add(featureBuilder.computeFeatures(m, bundle, asOf));
            }

            // Step 3: Build dataset
            MutableDataset<ClusterID> dataset = segmentationFeatureBuilder.buildDataset(featuresList);
            log.info("Built clustering dataset: {} examples", dataset.size());

            // Step 4: Train
            Model<ClusterID> model = kMeansTrainer.train(dataset);
            log.info("K-Means training complete");

            // Step 5: Predict all examples, group by cluster; collect assignments + vectors
            int n = featuresList.size();
            int[] assignments   = new int[n];
            double[][] featureMatrix = new double[n][];
            Map<Integer, List<Double>> clusterRevenues = new HashMap<>();

            for (int i = 0; i < n; i++) {
                CustomerAnalyticsFeatures f = featuresList.get(i);
                org.tribuo.Example<ClusterID> ex = segmentationFeatureBuilder.buildExample(f);
                ClusterID predicted = model.predict(ex).getOutput();
                int clusterId = predicted.getID();
                assignments[i]   = clusterId;
                featureMatrix[i] = segmentationFeatureBuilder.toFeatureVector(f);
                clusterRevenues.computeIfAbsent(clusterId, k -> new ArrayList<>())
                        .add(f.totalRevenue90d());
            }

            // Step 5b: Quality gate — silhouette ≥ 0.30
            ModelEvaluator.SegmentationEvaluationResult eval =
                    modelEvaluator.evaluateSegmentation(assignments, featureMatrix, 5);

            if (!eval.passedQualityGate()) {
                log.warn("Segmentation model did NOT pass quality gate (silhouette={:.3f}). Skipping promotion.",
                        eval.silhouetteScore());
                return new TrainingResult(false, null, Map.of(),
                        String.format("Quality gate failed: silhouette=%.3f < 0.30", eval.silhouetteScore()));
            }

            // Step 6: Sort clusters by average revenue, assign labels
            Map<Integer, String> clusterLabelMap = assignClusterLabels(clusterRevenues);
            log.info("Cluster labels: {}", clusterLabelMap);

            // Step 7: Register and promote
            UUID modelId = promoteModel(model, clusterLabelMap, eval, featuresList.size());
            long duration = System.currentTimeMillis() - start;
            log.info("=== Segmentation pipeline complete in {}ms, modelId={} silhouette={:.3f} ===",
                    duration, modelId, eval.silhouetteScore());

            return new TrainingResult(true, modelId, clusterLabelMap, null);

        } catch (Exception e) {
            log.error("Segmentation training pipeline failed: {}", e.getMessage(), e);
            return new TrainingResult(false, null, Map.of(), e.getMessage());
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Sort clusters by average revenue descending, assign labels in order:
     * HIGH_VALUE_GROWING (0), STABLE_MID_TIER (1), AT_RISK_DECLINING (2),
     * NEW_LOW_ACTIVITY (3), HIGH_VALUE_AT_RISK (4).
     */
    private Map<Integer, String> assignClusterLabels(Map<Integer, List<Double>> clusterRevenues) {
        List<Map.Entry<Integer, Double>> sorted = clusterRevenues.entrySet().stream()
                .map(e -> Map.entry(e.getKey(),
                        e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0)))
                .sorted(Comparator.comparingDouble(Map.Entry<Integer, Double>::getValue).reversed())
                .collect(Collectors.toList());

        Map<Integer, String> labels = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            String label = i < CLUSTER_LABELS.length ? CLUSTER_LABELS[i] : "SEGMENT_" + i;
            labels.put(sorted.get(i).getKey(), label);
        }
        return labels;
    }

    private UUID promoteModel(Model<ClusterID> model,
                               Map<Integer, String> clusterLabels,
                               ModelEvaluator.SegmentationEvaluationResult eval,
                               int trainingSize) throws Exception {
        byte[] modelBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(model);
            modelBytes = baos.toByteArray();
        }

        Map<String, Object> hyperparameters = new HashMap<>();
        hyperparameters.put("algorithm", "kmeans");
        hyperparameters.put("k", 5);
        hyperparameters.put("cluster_labels", clusterLabels);

        com.zuqi.domain.ai.AIModelRegistry registry = modelRegistry.registerModel(
                MODEL_NAME, "kmeans", hyperparameters, "training_pipeline");

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("training_size", trainingSize);
        metrics.put("num_clusters", clusterLabels.size());
        metrics.put("cluster_labels", clusterLabels.toString());
        metrics.put("silhouette_score", eval.silhouetteScore());
        metrics.put("min_cluster_size", eval.minClusterSize());

        modelRegistry.updateModelAfterTraining(registry.getId(), metrics, modelBytes,
                Map.of("feature_count", segmentationFeatureBuilder.getFeatureCount()));

        modelRegistry.promoteToActive(registry.getId());
        return registry.getId();
    }

    public record TrainingResult(
            boolean success,
            UUID modelId,
            Map<Integer, String> clusterLabels,
            String errorMessage
    ) {}
}
