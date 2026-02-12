/**
 * Training Pipeline Package
 *
 * <p>Spring Batch orchestration for ML model training, evaluation, and promotion.
 *
 * <p><b>Components:</b>
 * <ul>
 *   <li>TrainingPipelineJob - Spring Batch master training job</li>
 *   <li>FeatureComputationStep - Batch step: compute all features</li>
 *   <li>ModelTrainingStep - Batch step: train models</li>
 *   <li>ModelEvaluationStep - Batch step: evaluate on test set</li>
 *   <li>ModelPromotionStep - Batch step: promote if metrics pass</li>
 *   <li>DriftDetectionStep - Batch step: check for data drift</li>
 * </ul>
 *
 * <p><b>Implementation Plan Reference:</b> Phase 3, Task 3.2
 * <p><b>Blueprint Reference:</b> plan.md Section 7 (Training Pipeline Architecture)
 *
 * @since Phase 3
 */
package com.zuqi.ai.pipeline;
