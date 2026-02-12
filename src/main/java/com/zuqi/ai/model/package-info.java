/**
 * ML Model Management Package
 *
 * <p>Contains services for ML model lifecycle management:
 * <ul>
 *   <li>ModelRegistry - Model versioning, metadata, and binaries</li>
 *   <li>ModelLoader - Loads active models into memory for inference</li>
 *   <li>ModelTrainer - Generic training pipeline orchestrator</li>
 *   <li>ModelEvaluator - Model performance evaluation</li>
 *   <li>PredictionLogger - Audit log for all predictions</li>
 * </ul>
 *
 * <p><b>Model Lifecycle:</b> TRAINING → EVALUATING → ACTIVE → RETIRED
 *
 * <p>Only one ACTIVE version per model_name at a time. Models are hot-swappable
 * without application restart.
 *
 * <p><b>Implementation Plan Reference:</b> Phase 1, Task 1.3
 * <p><b>Blueprint Reference:</b> plan.md Section 5 (ML Model Management)
 *
 * @since Phase 1
 */
package com.zuqi.ai.model;
