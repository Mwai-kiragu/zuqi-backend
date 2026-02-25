# Zuqi AI Integration — Phased Implementation Plan

**Version:** 1.0
**Date:** February 2026
**Companion to:** Zuqi AI System Architecture Blueprint v1.0

---

## How to Use This Plan

Each phase builds on the previous one. Within each phase, tasks are ordered by dependency — complete them top to bottom. Each task includes what to build, which blueprint section it maps to , what it depends on, and a definition of done in plan.md file

**Do not skip phases.** Phase 1 is the foundation everything else depends on. Phases 2-6 build incrementally. Each phase delivers working, testable functionality.

---

## Phase 1: Foundation Infrastructure

**Goal:** Set up the AI infrastructure layer, feature engineering foundation, and model management system. No AI features are user-facing yet — this phase builds the plumbing.

**Estimated Duration:** 3-4 weeks

---

### 1.1 AI Package Structure & Dependencies

**What:** Create the `com.zuqi.ai` package hierarchy and add AI dependencies to the existing `pom.xml`.

**Tasks:**


- [ ] Add AI dependencies to existing `pom.xml`:
  - LangChain4j: `langchain4j-core`, `langchain4j-ollama`, `langchain4j-open-ai`, `langchain4j-pgvector`
  - Tribuo: `tribuo-core`, `tribuo-classification-xgboost`, `tribuo-regression-xgboost`, `tribuo-anomaly-core`
  - Timefold: `timefold-solver-spring-boot-starter`
  - GraphHopper: `graphhopper-core`
  - ONNX Runtime: `onnxruntime` (fallback)
  - Spring Batch: `spring-boot-starter-batch` (training pipelines)

- [ ] Create `com.zuqi.ai` package and sub-packages:
  - `config/`, `feature/`, `model/`, `credit/`, `demand/`, `anomaly/`, `prediction/`, `routing/`, `agent/`, `reporting/`, `pipeline/`, `monitoring/`

- [ ] Create placeholder classes in each package with TODO comments referencing blueprint sections

- [ ] Verify: application compiles and starts with new dependencies, no conflicts with existing libraries

**Depends on:** Nothing
**Definition of Done:** Package structure exists, dependencies resolve, application starts, no functionality yet

---

### 1.2 Database Schema — AI Tables

**What:** Create Flyway migrations for all AI database tables defined in Blueprint Section 5.1. Migrations continue the existing sequence (V15+).

**Tasks:**
- [ ] Create migration for `ai_model_registry` table
- [ ] Create migration for `ai_predictions` table
- [ ] Create migration for `ai_model_performance` table
- [ ] Create migration for `ai_demand_forecasts` table
- [ ] Create migration for `ai_anomaly_alerts` table
- [ ] Create migration for `ai_recommendations` table
- [ ] Create migration for `ai_delivery_routes` table
- [ ] Create migration for `ai_merchant_embeddings` table (RAG for credit scoring)
- [ ] Create migration for `ai_recommendation_embeddings` table (RAG for agent context)
- [ ] Create all indexes defined in the blueprint (including pgvector similarity indexes)
- [ ] Verify migrations run cleanly on existing database
- [ ] Create JPA entities for each table under `com.zuqi.ai.model.entity` package
- [ ] Create Spring Data JPA repositories for each AI entity under `com.zuqi.ai.model.repository` package

**Depends on:** 1.1
**Definition of Done:** All tables created via Flyway, AI entities and repositories compile, application starts successfully

---

### 1.3 Model Registry Service

**What:** Build the `ModelRegistry` and `ModelLoader` services defined in Blueprint Section 5.2 and 5.3.

**Tasks:**
- [ ] Implement `ModelRegistry` service:
  - `registerModel(name, version, algorithm, binary, metrics, hyperparameters)` → saves to `ai_model_registry`
  - `promoteModel(name, version)` → sets status to ACTIVE, retires previous ACTIVE version
  - `getActiveModel(name)` → returns currently ACTIVE model metadata
  - `getModelHistory(name)` → returns all versions for a model
  - `retireModel(name, version)` → sets status to RETIRED
- [ ] Implement `ModelLoader` service:
  - Loads all ACTIVE Tribuo models into `ConcurrentHashMap<String, Model<?>>` on startup
  - `getModel(name)` → returns loaded model or null
  - `refreshModel(name)` → hot-swaps model when new version promoted
  - Graceful degradation: returns null (not exception) if no model exists
- [ ] Implement `PredictionLogger` service:
  - `logPrediction(modelName, modelVersion, entityType, entityId, inputHash, prediction, confidence)` → saves to `ai_predictions`
  - `logOverride(predictionId, overrideValue, overrideBy, reason)` → updates prediction record
- [ ] Write unit tests for all three services

**Depends on:** 1.2
**Definition of Done:** Model registry CRUD operations work, model loader loads/swaps models, predictions are logged with audit trail

---

### 1.4 Feature Engineering Services — MerchantFeatureService

**What:** Build the first and most critical feature service. Almost every AI module consumes merchant features.

**Tasks:**
- [ ] Define `MerchantFeatures` record/DTO containing all features listed in Blueprint Section 4.2 (MerchantFeatureService):
  - Order features: total_orders, order_frequency_per_week, avg_order_value, order_value_trend_slope_12w, order_consistency_stddev, cancellation_rate, return_rate, days_since_last_order, unique_skus_ordered, top_sku_concentration
  - Payment features: total_payments, on_time_payment_pct, avg_days_to_pay, worst_days_to_pay, partial_payment_frequency, payment_method_distribution, consecutive_on_time_streak, total_overdue_amount
  - Credit features: current_credit_limit, current_utilization_ratio, peak_utilization_ratio, utilization_trend_slope, limit_increase_count, days_since_last_limit_change
  - Profile features: business_category_encoded, relationship_tenure_days, verification_status, geographic_cluster
- [ ] Implement `MerchantFeatureService`:
  - `computeFeatures(merchantId)` → computes current features from existing repositories
  - `computeFeatures(merchantId, asOfDate)` → computes historical features as of a past date (for training)
  - Each feature computation is a separate private method for testability
- [ ] Implement Redis caching in `MerchantFeatureService`:
  - Cache key: `merchant_features:{merchantId}`
  - TTL: 24 hours
  - Cache invalidation on relevant events
- [ ] Write unit tests with mock repository data
- [ ] Write integration test verifying feature computation against known test data

**Depends on:** 1.1, existing Order/Payment/Merchant/CreditLimit repositories
**Definition of Done:** Can compute complete merchant feature vector for any merchant, with caching, with historical mode for training

---

### 1.5 Feature Engineering Services — OrderFeatureService

**What:** Build the demand/order feature service used by demand forecasting and order suggestions.

**Tasks:**
- [ ] Define `DemandFeatures` record/DTO containing all features listed in Blueprint Section 4.2 (OrderFeatureService):
  - Lag features: qty_1w_ago, qty_2w_ago, qty_3w_ago, qty_4w_ago, rolling_avg_4w, rolling_avg_12w, trend_direction
  - Temporal features: day_of_week, week_of_month, month_of_year, is_holiday, is_payday_week, is_ramadan, is_christmas_season
  - Merchant context: merchant_category, merchant_size_tier, merchant_credit_status, merchant_tenure
  - SKU context: product_category, price_tier, is_promotional, typical_shelf_life
- [ ] Implement `OrderFeatureService`:
  - `computeFeatures(merchantId, skuId)` → current features
  - `computeFeatures(merchantId, skuId, asOfDate)` → historical features
  - Kenya holiday calendar implementation (public holidays, Ramadan dates, paydays)
- [ ] Implement Redis caching (TTL: refreshed nightly by batch job)
- [ ] Write unit tests and integration tests

**Depends on:** 1.1, existing Order/OrderItem/Product/Merchant repositories
**Definition of Done:** Can compute complete demand feature vector for any merchant-SKU combination

---

### 1.6 Feature Engineering Services — PaymentFeatureService

**What:** Build payment behavior features used by anomaly detection and credit scoring.

**Tasks:**
- [ ] Define `PaymentFeatures` record/DTO (per-payment features for anomaly detection):
  - days_to_payment_vs_merchant_avg, amount_vs_invoice_amount_ratio, payment_method_encoded, hour_of_day, is_partial, gap_since_last_payment_days
- [ ] Define `MerchantPaymentTrendFeatures` record/DTO (merchant-level trends for distress):
  - days_to_pay_trend_3m, order_frequency_trend_3m, credit_utilization_trajectory, partial_payment_freq_trend, avg_order_value_trend
- [ ] Implement `PaymentFeatureService`:
  - `computePaymentFeatures(paymentId)` → per-payment features
  - `computeMerchantTrendFeatures(merchantId)` → merchant trend features
  - Historical mode for both
- [ ] Write tests

**Depends on:** 1.1, existing Payment/Invoice/Order/Merchant repositories
**Definition of Done:** Can compute payment features per transaction and per merchant

---

### 1.7 Feature Engineering Services — InventoryFeatureService

**What:** Build inventory features used by shrinkage detection and stockout prediction.

**Tasks:**
- [ ] Define `InventoryFeatures` record/DTO:
  - current_stock, expected_stock, discrepancy, discrepancy_pct, manual_adjustment_count_7d, adjustment_time_distribution, adjusting_user_ids, consumption_rate_7d, consumption_rate_30d, consumption_trend, pending_reserved_qty, expected_incoming_qty
- [ ] Implement `InventoryFeatureService`:
  - `computeFeatures(warehouseId, skuId)` → current features
  - Historical mode
- [ ] Write tests

**Depends on:** 1.1, existing StockMovement/Inventory/Warehouse/Order repositories
**Definition of Done:** Can compute inventory features per warehouse-SKU combination

---

### 1.8 Feature Engineering Services — SalesRepFeatureService

**What:** Build sales rep performance features.

**Tasks:**
- [ ] Define `SalesRepFeatures` record/DTO:
  - visit_count_vs_target, order_conversion_rate, total_order_value, avg_order_value, new_merchants_acquired, collection_rate, route_adherence_pct, territory_penetration_pct
- [ ] Implement `SalesRepFeatureService`:
  - `computeFeatures(salesRepId, periodStart, periodEnd)` → features for a time period
- [ ] Write tests

**Depends on:** 1.1, existing Order/Merchant/User repositories
**Definition of Done:** Can compute sales rep features per rep per period

---

### 1.9 FeatureStore — Centralized Access

**What:** Build the `FeatureStore` service that provides unified access to all feature services with caching.

**Tasks:**
- [ ] Implement `FeatureStore`:
  - `getMerchantFeatures(merchantId)` → delegates to `MerchantFeatureService` with caching
  - `getDemandFeatures(merchantId, skuId)` → delegates to `OrderFeatureService`
  - `getPaymentFeatures(paymentId)` → delegates to `PaymentFeatureService`
  - `getInventoryFeatures(warehouseId, skuId)` → delegates to `InventoryFeatureService`
  - `getSalesRepFeatures(repId, period)` → delegates to `SalesRepFeatureService`
  - Bulk retrieval methods for batch operations: `getAllMerchantFeatures(distributorId)`
  - Cache management: invalidation, refresh, warm-up
- [ ] Write integration tests verifying caching behavior

**Depends on:** 1.4, 1.5, 1.6, 1.7, 1.8
**Definition of Done:** Single entry point for all feature retrieval, caching works, bulk retrieval works

---

### 1.10 Spring Event Infrastructure

**What:** Set up the event publishing system for real-time AI triggers.

**Tasks:**
- [ ] Define AI event records:
  - `PaymentRecordedEvent(paymentId, merchantId, distributorId, amount, paymentMethod, occurredAt)`
  - `StockAdjustedEvent(warehouseId, skuId, adjustmentType, quantity, userId, occurredAt)`
  - `OrderCreatedEvent(orderId, merchantId, distributorId, totalAmount, occurredAt)`
  - `MerchantCreatedEvent(merchantId, distributorId, occurredAt)`
  - `DeliveryCompletedEvent(routeId, merchantId, driverId, occurredAt)`
- [ ] Add `ApplicationEventPublisher.publishEvent()` calls to existing services:
  - `PaymentService` → publish `PaymentRecordedEvent` after payment recording
  - `InventoryService` → publish `StockAdjustedEvent` after stock adjustment
  - `OrderService` → publish `OrderCreatedEvent` after order creation
  - `MerchantService` → publish `MerchantCreatedEvent` after merchant onboarding
- [ ] Create placeholder `@EventListener` methods in AI modules (no-op initially, will be implemented in later phases)
- [ ] Write tests verifying events are published

**Depends on:** 1.1, existing service classes
**Definition of Done:** Events published from existing workflows, placeholder listeners in AI modules

---

### 1.11 AI Health Endpoint

**What:** Build the `/v1/ai/system` endpoints for monitoring.

**Tasks:**
- [ ] Create `AIHealthController` with endpoints:
  - `GET /v1/ai/system/health` → returns status of all AI components (model registry, feature services, LLM connectivity)
  - `GET /v1/ai/system/models` → returns list of active models and versions from registry
  - `GET /v1/ai/system/models/{modelName}/performance` → returns performance metrics
- [ ] Add Casbin RBAC rules: accessible by SUPER_ADMIN, ADMIN only
- [ ] Write tests

**Depends on:** 1.3
**Definition of Done:** AI system health visible via API

---

### Phase 1 Checkpoint

**Before moving to Phase 2, verify:**
- [ ] All AI database tables exist and migrations run
- [ ] Model registry supports full CRUD lifecycle
- [ ] All 5 feature services compute correct features with tests passing
- [ ] FeatureStore provides cached, unified access
- [ ] Events are published from existing workflows
- [ ] AI health endpoint returns system status
- [ ] Application starts and all existing functionality still works (no regressions)

---

## Phase 2: LLM Integration & Credit Scoring

**Goal:** Set up LangChain4j with Ollama, implement the first user-facing AI feature (credit risk scoring), and establish the LLM patterns used by all subsequent LLM features.

**Estimated Duration:** 3-4 weeks

---

### 2.1 Ollama Setup

**What:** Deploy Ollama with a local LLM model.

**Tasks:**
- [ ] Add Ollama to Docker Compose / deployment configuration
- [ ] Configure GPU passthrough (NVIDIA A10G/L4) for the Ollama container
- [ ] Pull Qwen 2.5 32B model (or Mixtral 8x7B as alternative)
- [ ] Pull embedding model (nomic-embed-text or similar)
- [ ] Verify Ollama responds on localhost:11434
- [ ] Test model inference via curl: `curl http://localhost:11434/api/generate`
- [ ] Document model selection rationale and configuration

**Depends on:** GPU infrastructure provisioned
**Definition of Done:** Ollama running locally with chosen model, responding to requests

---

### 2.2 LangChain4j Configuration

**What:** Add LangChain4j dependencies and configure providers.

**Tasks:**
- [ ] Add Maven dependencies:
  - `langchain4j-core`
  - `langchain4j-ollama` (local LLM)
  - `langchain4j-open-ai` (cloud fallback)
  - `langchain4j-pgvector` (embedding store)
- [ ] Create `LangChain4jConfig` configuration class:
  - Ollama `ChatLanguageModel` bean (primary) — configure base URL, model name, temperature defaults
  - OpenAI/Anthropic `ChatLanguageModel` bean (fallback) — configure API key, model name
  - Ollama `EmbeddingModel` bean — for RAG embeddings
  - `PgVectorEmbeddingStore` bean — connected to existing PostgreSQL
- [ ] Create Resilience4j circuit breaker configuration for LLM calls:
  - Circuit breaker: open after 5 failures in 60 seconds
  - Timeout: 30 seconds (local), 60 seconds (cloud)
  - Retry: 2 retries with exponential backoff
  - Fallback chain: local → cloud → graceful degradation
- [ ] Test basic LLM call: send simple prompt, receive response
- [ ] Test embedding generation: embed sample text, store in pgvector, retrieve by similarity

**Depends on:** 2.1
**Definition of Done:** LangChain4j configured with local and cloud providers, RAG embedding pipeline working

---

### 2.3 RAG Infrastructure — pgvector Setup

**What:** Configure pgvector for storing and retrieving embeddings used in RAG contexts.

**Tasks:**
- [ ] Verify pgvector extension is enabled in PostgreSQL (should already exist per backend stack)
- [ ] Create embedding tables via Flyway migration:
  - `ai_merchant_embeddings` — merchant profile embeddings for credit scoring similarity
  - `ai_recommendation_embeddings` — past recommendation embeddings
- [ ] Implement `MerchantEmbeddingService`:
  - `embedMerchant(merchantId)` → computes merchant features → converts to text summary → generates embedding → stores in pgvector
  - `findSimilarMerchants(merchantId, limit)` → retrieves N most similar merchant profiles
  - Batch mode: `embedAllMerchants(distributorId)` for initial population
- [ ] Create a scheduled job to refresh embeddings nightly
- [ ] Write tests verifying similarity search returns sensible results

**Depends on:** 2.2, 1.4 (MerchantFeatureService)
**Definition of Done:** Can embed merchant profiles and retrieve similar merchants via cosine similarity

---

### 2.4 Credit Scoring — CreditFeatureBuilder

**What:** Build the bridge between raw features and the LLM/ML credit evaluation inputs.

**Tasks:**
- [ ] Implement `CreditFeatureBuilder`:
  - `buildLlmProfile(merchantId)` → calls `MerchantFeatureService`, formats into `MerchantCreditProfile` DTO optimized for LLM consumption (human-readable labels, contextual descriptions)
  - `buildMlFeatureVector(merchantId)` → calls `MerchantFeatureService`, formats into Tribuo `Example<Label>` for ML model consumption (numeric vector)
  - `buildPeerContext(merchantId)` → calls `MerchantEmbeddingService.findSimilarMerchants()`, formats peer comparison summary
- [ ] Define `MerchantCreditProfile` DTO:
  - All merchant features in labeled, readable format
  - Peer comparison summary
  - Evaluation timestamp
- [ ] Write tests

**Depends on:** 1.4, 2.3
**Definition of Done:** Can produce both LLM-friendly and ML-friendly credit input from same underlying data

---

### 2.5 Credit Scoring — CreditScoringAiService

**What:** Build the LLM-based credit scoring service. This is the first user-facing AI feature.

**Tasks:**
- [ ] Define `CreditEvaluation` output POJO:
  - `grade` (A, B, C, D, F)
  - `recommendedLimitKes` (BigDecimal)
  - `confidenceScore` (double, 0-1)
  - `riskFactors` (List<String>, max 5)
  - `reasoning` (String)
- [ ] Create `CreditScoringAiService` as LangChain4j `@AiService` interface:
  - Method: `evaluate(MerchantCreditProfile profile, String peerContext) → CreditEvaluation`
  - System prompt: define credit evaluation rubric, grading criteria, output format
  - Temperature: 0.1
  - Provider: Ollama primary, cloud fallback
- [ ] Implement business rules overlay:
  - Max limit cap for first evaluation
  - Limit cannot exceed X% of monthly order value
  - 90+ day overdue → automatic F
  - < 30 day tenure → max grade C
- [ ] Implement evaluation orchestrator (`CreditScoringOrchestrator`):
  - Step 1: `CreditFeatureBuilder.buildLlmProfile(merchantId)`
  - Step 2: `CreditFeatureBuilder.buildPeerContext(merchantId)`
  - Step 3: `CreditScoringAiService.evaluate(profile, peerContext)`
  - Step 4: Apply business rules overlay
  - Step 5: Route to auto-approve or human review based on confidence and limit thresholds
  - Step 6: Log to `ai_predictions` via `PredictionLogger`
  - Step 7: Store evaluation in database
- [ ] Write comprehensive tests:
  - Unit test: business rules overlay with edge cases
  - Integration test: full evaluation flow with mock LLM response
  - Test: circuit breaker fallback from Ollama to cloud
  - Test: graceful degradation when all LLM providers fail

**Depends on:** 2.2, 2.4
**Definition of Done:** Can trigger credit evaluation for any merchant, receive structured grade with reasoning, audit trail stored

---

### 2.6 Credit Scoring — REST API

**What:** Expose credit scoring via API endpoints.

**Tasks:**
- [ ] Create `AiCreditController`:
  - `POST /v1/ai/credit/evaluate/{merchantId}` → triggers evaluation, returns `CreditEvaluation`
  - `GET /v1/ai/credit/evaluations/{merchantId}` → returns evaluation history from `ai_predictions`
  - `GET /v1/ai/credit/score/{merchantId}` → returns current active credit score
- [ ] Add Casbin RBAC rules: accessible by DISTRIBUTOR_ADMIN, FINANCE
- [ ] Add request validation and error handling
- [ ] Write API tests

**Depends on:** 2.5
**Definition of Done:** Credit scoring accessible via REST API, authorized, tested

---

### 2.7 Credit Scoring — Event-Driven Triggers

**What:** Wire credit scoring to automatic triggers.

**Tasks:**
- [ ] Implement `@EventListener` for `MerchantCreatedEvent`:
  - Triggers credit evaluation for newly onboarded merchants
  - Async execution (`@Async`)
- [ ] Implement scheduled monthly re-evaluation:
  - `@Scheduled` job iterates all active merchants per distributor
  - Calls `CreditScoringOrchestrator.evaluate()` for each
  - Rate-limited to avoid overwhelming LLM
- [ ] Write tests for both trigger paths

**Depends on:** 2.5, 1.10
**Definition of Done:** Credit scoring runs automatically on merchant onboarding and monthly schedule

---

### 2.8 Prometheus Metrics — LLM Layer

**What:** Add observability for LLM calls.

**Tasks:**
- [ ] Add Micrometer metrics:
  - `zuqi_ai_llm_requests_total` (counter, tags: provider, model, module)
  - `zuqi_ai_llm_latency_seconds` (histogram, tags: provider, model, module)
  - `zuqi_ai_llm_errors_total` (counter, tags: provider, model, error_type)
- [ ] Instrument `CreditScoringAiService` with these metrics
- [ ] Verify metrics appear in Prometheus
- [ ] Create basic Grafana dashboard for LLM metrics

**Depends on:** 2.5, existing Prometheus/Grafana setup
**Definition of Done:** LLM call volume, latency, and error rates visible in Grafana

---

### Phase 2 Checkpoint

**Before moving to Phase 3, verify:**
- [ ] Ollama running with local LLM, LangChain4j connected
- [ ] RAG pipeline working (embed merchants, retrieve similar)
- [ ] Credit scoring produces consistent, reasonable evaluations
- [ ] Business rules correctly constrain LLM recommendations
- [ ] Audit trail stored for every evaluation
- [ ] API endpoints working with proper authorization
- [ ] Automatic triggers (onboarding, monthly) working
- [ ] LLM metrics visible in Grafana

---

## Phase 3: Classical ML — Demand Forecasting & Order Suggestions

**Goal:** Introduce Tribuo, build the first ML model (demand forecasting), and deliver the second user-facing feature (order suggestions for sales reps).

**Estimated Duration:** 3-4 weeks

---

### 3.1 Tribuo Setup

**What:** Add Tribuo dependencies and configure the ML infrastructure.

**Tasks:**
- [ ] Add Maven dependencies:
  - `tribuo-core`
  - `tribuo-classification-xgboost`
  - `tribuo-regression-xgboost`
  - `tribuo-anomaly-libsvm` (for Isolation Forest — or `tribuo-anomaly-core`)
  - `tribuo-evaluation`
- [ ] Create `TribuoConfig` configuration class:
  - Default XGBoost hyperparameters for regression and classification
  - Model serialization/deserialization utilities
  - Feature normalization configuration
- [ ] Verify Tribuo loads correctly: train a trivial model on dummy data to confirm library works
- [ ] Create Tribuo utility class: `TribuoFeatureConverter`
  - Converts feature DTOs (from feature services) into Tribuo `Example` objects
  - Handles feature name mapping and encoding
  - Supports both `Regressor` and `Label` output types

**Depends on:** 1.3 (ModelRegistry)
**Definition of Done:** Tribuo libraries loaded, can train and serialize a simple model

---

### 3.2 Training Pipeline Infrastructure

**What:** Build the generic training pipeline using Spring Batch (Blueprint Section 7).

**Tasks:**
- [ ] Create `TrainingPipelineJob` — Spring Batch job template:
  - Parameterized by model name, date range, hyperparameters
  - Step 1: `FeatureComputationStep` — calls feature services for training date range
  - Step 2: `DataSplitStep` — time-based 80/20 train/test split
  - Step 3: `ModelTrainingStep` — trains Tribuo model on training split
  - Step 4: `ModelEvaluationStep` — evaluates on test split, computes metrics
  - Step 5: `ModelPromotionStep` — compares against active model, promotes if quality gates pass
- [ ] Implement quality gates in `ModelPromotionStep`:
  - Configurable minimum thresholds per model (e.g., MAE < X for regression, AUC > Y for classification)
  - New model must not regress more than Z% from current active model
  - If gates fail: model stays in EVALUATING, alert generated
- [ ] Create `ModelEvaluator` utility:
  - Regression metrics: MAE, RMSE, MAPE, R²
  - Classification metrics: accuracy, precision, recall, F1, AUC-ROC
  - Stores metrics in `ai_model_performance` table
- [ ] Write tests for pipeline with mock data

**Depends on:** 1.3, 1.9
**Definition of Done:** Generic training pipeline can train any Tribuo model, evaluate it, and promote to active

---

### 3.3 Demand Forecasting — DemandFeatureBuilder

**What:** Build the feature builder specific to demand forecasting.

**Tasks:**
- [ ] Implement `DemandFeatureBuilder`:
  - `buildTrainingDataset(distributorId, startDate, endDate)` → generates Tribuo `MutableDataset<Regressor>` from historical data
    - For each merchant-SKU-date combination in the range: compute features as of that date, label = actual quantity ordered
  - `buildInferenceExample(merchantId, skuId)` → generates single Tribuo `Example<Regressor>` for prediction
  - Feature encoding: categorical features (merchant_category, product_category) encoded as one-hot or ordinal
- [ ] Handle edge cases:
  - Merchants with < 4 weeks history: exclude from training, use simple heuristics for suggestions
  - SKUs with sparse orders: use category-level aggregates for lag features
  - Zero-quantity periods: explicitly include as training examples (important for learning non-order patterns)
- [ ] Write tests with synthetic data verifying correct feature computation

**Depends on:** 1.5 (OrderFeatureService), 3.1
**Definition of Done:** Can produce complete training dataset and inference examples for demand model

---

### 3.4 Demand Forecasting — Model Training

**What:** Train the demand forecasting XGBoost regression model.

**Tasks:**
- [ ] Implement `DemandForecaster`:
  - `train(distributorId)` → builds training dataset via `DemandFeatureBuilder`, trains XGBoost regression model, stores via `ModelRegistry`
  - XGBoost hyperparameters: max_depth=6, learning_rate=0.1, n_estimators=200, subsample=0.8
  - Training data: 6-12 months of order history
- [ ] Create `DemandModelTrainingJob` — Spring Batch job using the generic pipeline:
  - Runs weekly
  - Trains per distributor (multi-tenant isolation)
  - Evaluates on last 2 weeks of data
  - Quality gate: MAPE < 40% (initial, will tighten as data accumulates)
- [ ] Schedule job: weekly, 2:00 AM EAT
- [ ] Write tests: train on synthetic data, verify predictions are reasonable

**Depends on:** 3.2, 3.3
**Definition of Done:** Demand model trains weekly, registers in model registry, passes quality gates

---

### 3.5 Demand Forecasting — Batch Prediction

**What:** Nightly batch job that generates forecasts for all merchant-SKU combinations.

**Tasks:**
- [ ] Implement `DemandForecastJob` (Spring Batch):
  - Step 1: load active demand model from `ModelLoader`
  - Step 2: iterate all active merchant-SKU combinations per distributor
  - Step 3: compute features via `DemandFeatureBuilder.buildInferenceExample()`
  - Step 4: run inference, get predicted quantity
  - Step 5: store in `ai_demand_forecasts` table (merchant_id, sku_id, forecast_date, predicted_qty, confidence, model_version)
  - Step 6: aggregate to warehouse-SKU level for inventory planning
  - Forecast horizon: next 7 days
- [ ] Handle missing model gracefully: if no active model exists, skip forecasting (no error)
- [ ] Schedule: nightly, 3:00 AM EAT (after feature refresh)
- [ ] Add Prometheus metrics: `zuqi_ai_forecast_records_generated`
- [ ] Write tests

**Depends on:** 3.4
**Definition of Done:** Nightly forecasts generated for all merchant-SKU combinations, stored in database

---

### 3.6 Order Suggestions — Service & API

**What:** Build the order suggestion service and expose via API for sales rep mobile app.

**Tasks:**
- [ ] Implement `OrderSuggestionService`:
  - `getSuggestions(merchantId)`:
    - Retrieve pre-computed forecasts from `ai_demand_forecasts` for this merchant
    - Filter: remove out-of-stock SKUs (check inventory), remove SKUs that would exceed credit limit
    - Rank: by confidence (descending), then by margin contribution, then by recency
    - Return top 15 suggestions as `OrderSuggestion` DTOs (skuId, productName, suggestedQty, confidence, lastOrderedDate)
  - Fallback: if no forecast exists (new merchant, no model), return top SKUs by category popularity
- [ ] Create `AiDemandController`:
  - `GET /v1/ai/demand/suggestions/{merchantId}` → returns order suggestions
  - `GET /v1/ai/demand/forecast/{merchantId}` → returns raw forecast data
  - `GET /v1/ai/demand/forecast/warehouse/{warehouseId}` → returns aggregated warehouse forecasts
- [ ] Add Casbin RBAC: suggestions accessible by SALES_REP, DISTRIBUTOR_ADMIN; forecasts by DISTRIBUTOR_ADMIN, WAREHOUSE_MANAGER
- [ ] Write API tests

**Depends on:** 3.5
**Definition of Done:** Sales reps can request order suggestions via API, responses return within 500ms

---

### 3.7 Prometheus Metrics — ML Layer

**What:** Add observability for ML model inference.

**Tasks:**
- [ ] Add metrics:
  - `zuqi_ai_model_inference_total` (counter, tags: model_name, model_version)
  - `zuqi_ai_model_inference_latency_ms` (histogram, tags: model_name)
  - `zuqi_ai_training_runs_total` (counter, tags: model_name, status)
  - `zuqi_ai_model_training_duration_seconds` (gauge, tags: model_name)
- [ ] Instrument demand forecaster with these metrics
- [ ] Add to Grafana dashboard

**Depends on:** 3.5
**Definition of Done:** ML model training and inference metrics visible in Grafana

---

### Phase 3 Checkpoint

**Before moving to Phase 4, verify:**
- [ ] Tribuo integrated, XGBoost training works
- [ ] Generic training pipeline functional (train → evaluate → promote)
- [ ] Demand model trains weekly with acceptable metrics
- [ ] Nightly forecasts generated and stored
- [ ] Order suggestions API returns relevant products
- [ ] Fallback works when no model exists
- [ ] ML metrics visible in Grafana

---

## Phase 4: Anomaly Detection & Predictive Alerts

**Goal:** Deploy anomaly detection across inventory, payments, and data quality. Add stockout prediction and sales rep performance monitoring.

**Estimated Duration:** 3-4 weeks

---

### 4.1 Shrinkage Detection — Model & Training

**Tasks:**
- [ ] Implement `ShrinkageDetector`:
  - Algorithm: Tribuo Isolation Forest
  - Input: inventory features from `InventoryFeatureService`
  - `train(distributorId)` → trains on 3 months of inventory movement data
  - `score(warehouseId, skuId)` → returns anomaly score (0-1)
- [ ] Create training job: weekly, registered in model registry
- [ ] Configure anomaly threshold (start conservative: 0.8)
- [ ] Write tests

**Depends on:** 1.7, 3.1, 3.2
**Definition of Done:** Shrinkage model trains and scores inventory discrepancies

---

### 4.2 Payment Anomaly Detection — Model & Training

**Tasks:**
- [ ] Implement `PaymentAnomalyDetector`:
  - Algorithm: Tribuo Isolation Forest
  - Input: per-payment features from `PaymentFeatureService`
  - `train(distributorId)` → trains on 6 months of payment patterns
  - `score(paymentId)` → returns anomaly score
- [ ] Create training job: weekly
- [ ] Configure threshold
- [ ] Write tests

**Depends on:** 1.6, 3.1, 3.2
**Definition of Done:** Payment anomaly model trains and scores individual payments

---

### 4.3 Data Quality Detection

**Tasks:**
- [ ] Implement `DataQualityDetector` — Tier 1 (rules):
  - Order quantity > 10x merchant average → flag
  - Coordinates > 50km from registered address → flag
  - Future-dated invoices → flag
  - Price override > 30% deviation → flag
  - Duplicate orders within 5 minutes → flag
  - Returns: `DataQualityResult` with pass/fail and list of violations
- [ ] Implement `DataQualityDetector` — Tier 2 (ML):
  - Algorithm: Tribuo Isolation Forest on data entry patterns
  - Trains weekly on historical entries
  - Catches suspicious combinations
- [ ] Wire Tier 1 into `OrderCreatedEvent` listener (synchronous validation)
- [ ] Wire Tier 2 into nightly batch
- [ ] Write tests for each rule and ML detection

**Depends on:** 1.5, 1.6, 1.7, 3.1
**Definition of Done:** Data quality rules fire on order creation, ML catches subtle patterns in batch

---

### 4.4 AlertService

**What:** Centralized alert management for all anomaly detectors.

**Tasks:**
- [ ] Implement `AlertService`:
  - `createAlert(type, severity, entityType, entityId, distributorId, anomalyScore, description, context)` → saves to `ai_anomaly_alerts`
  - Deduplication: same entity + type within 24 hours → update existing, don't create new
  - Severity classification: configurable thresholds per alert type (LOW/MEDIUM/HIGH/CRITICAL)
- [ ] Create `AiAnomalyController`:
  - `GET /v1/ai/anomaly/alerts` → list alerts (filterable by type, severity, status, date range)
  - `GET /v1/ai/anomaly/alerts/summary` → alert counts by type and severity for dashboard
  - `PUT /v1/ai/anomaly/alerts/{alertId}/acknowledge` → mark acknowledged
  - `PUT /v1/ai/anomaly/alerts/{alertId}/resolve` → mark resolved with resolution notes
- [ ] Add Casbin RBAC: accessible by DISTRIBUTOR_ADMIN, WAREHOUSE_MANAGER, FINANCE
- [ ] Write tests

**Depends on:** 1.2 (ai_anomaly_alerts table)
**Definition of Done:** Alerts created, deduplicated, queryable, resolvable via API

---

### 4.5 Event-Driven Anomaly Scoring

**What:** Wire anomaly detectors to real-time events.

**Tasks:**
- [ ] Implement `@EventListener` for `StockAdjustedEvent`:
  - Compute inventory features → score via `ShrinkageDetector` → create alert if above threshold
  - `@Async` execution
- [ ] Implement `@EventListener` for `PaymentRecordedEvent`:
  - Compute payment features → score via `PaymentAnomalyDetector` → create alert if above threshold
  - `@Async` execution
- [ ] Implement `@EventListener` for `OrderCreatedEvent`:
  - Run `DataQualityDetector` Tier 1 rules
  - Create alert for any violations
- [ ] Handle model-not-loaded gracefully (skip scoring, no error)
- [ ] Write tests for each event flow

**Depends on:** 4.1, 4.2, 4.3, 4.4, 1.10
**Definition of Done:** Anomalies detected in real-time as events flow through the system

---

### 4.6 Stockout Prediction

**Tasks:**
- [ ] Implement `StockoutPredictor`:
  - Algorithm: Tribuo `XGBoostClassificationTrainer`
  - Input: inventory features + demand forecasts from `ai_demand_forecasts`
  - Output: stockout probability within 3, 5, 7 days
  - `train(distributorId)` → trains on historical stockout events
  - `predict(warehouseId, skuId)` → returns probabilities for each horizon
- [ ] Create training job: weekly (runs after demand forecast job)
- [ ] Implement `PredictionAlertService` (stockout):
  - Nightly batch: predict for all warehouse-SKU combinations
  - If probability > 70%: create alert with predicted date and recommended reorder quantity
  - Route through `AlertService`
- [ ] Create `AiPredictionController`:
  - `GET /v1/ai/prediction/stockout/{warehouseId}` → returns stockout predictions
- [ ] Write tests

**Depends on:** 3.5 (demand forecasts), 4.4
**Definition of Done:** Stockout predictions generated nightly, alerts created for high-risk items

---

### 4.7 Sales Rep Underperformance Detection

**Tasks:**
- [ ] Implement `RepPerformancePredictor`:
  - Algorithm: Tribuo `XGBoostRegressionTrainer`
  - Input: sales rep features from `SalesRepFeatureService`
  - Output: expected performance score
  - Underperformance: actual < expected - threshold for 2+ consecutive periods
- [ ] Create training job: monthly
- [ ] Implement `PredictionAlertService` (rep performance):
  - Weekly batch: predict expected performance for all reps
  - Compare against actual
  - Alert if sustained underperformance detected
- [ ] Add to `AiPredictionController`:
  - `GET /v1/ai/prediction/rep-performance` → returns rep predictions and flags
- [ ] Write tests

**Depends on:** 1.8, 3.2, 4.4
**Definition of Done:** Rep performance monitored weekly, sustained underperformance flagged

---

### Phase 4 Checkpoint

**Before moving to Phase 5, verify:**
- [ ] Shrinkage detection scoring on stock adjustments in real-time
- [ ] Payment anomaly detection scoring on payment events in real-time
- [ ] Data quality rules firing on order creation
- [ ] AlertService deduplicating and managing alerts
- [ ] Stockout predictions running nightly after demand forecasts
- [ ] Rep performance monitored weekly
- [ ] All alert endpoints working with proper authorization
- [ ] No impact on existing system performance (async execution verified)

---

## Phase 5: Route Optimization

**Goal:** Implement delivery route optimization using edTimefold and GraphHopper.

**Estimated Duration:** 2-3 weeks

---

### 5.1 GraphHopper Setup

**Tasks:**
- [ ] Add GraphHopper Java dependency to Maven
- [ ] Download OpenStreetMap data for Kenya (`.osm.pbf` file)
- [ ] Configure GraphHopper to load Kenya road network:
  - Vehicle profiles: car (for delivery vehicles)
  - Routing algorithm: CH (Contraction Hierarchies) for fast queries
- [ ] Implement `DistanceMatrixService`:
  - `getDistance(fromLat, fromLng, toLat, toLng)` → returns distance (km) and duration (minutes)
  - `computeMatrix(locations)` → computes full distance/time matrix for list of locations
  - Redis caching for frequently queried location pairs (TTL: 30 days)
- [ ] Write tests verifying distance calculations for known Kenya routes

**Depends on:** Nothing (independent module)
**Definition of Done:** Can compute travel distances and times between any two points in Kenya

---

### 5.2 Timefold Solver Configuration

**Tasks:**
- [ ] Add Timefold Spring Boot starter dependency to Maven
- [ ] Define planning domain entities (Blueprint Section 6.5):
  - `Vehicle` — capacity_kg, capacity_volume, start_location, driver_id, max_hours
  - `DeliveryStop` — @PlanningEntity: merchant_location, order_weight, order_volume, time_window_start, time_window_end, priority, assigned vehicle, sequence index
  - `RoutePlan` — @PlanningSolution: list of vehicles, list of stops, score
- [ ] Define constraint provider:
  - Hard constraints: vehicle capacity (weight + volume), driver max hours, all orders assigned
  - Soft constraints: minimize total distance, minimize total time, balance workload across drivers, prefer delivery within time windows
- [ ] Configure solver:
  - Construction heuristic: FIRST_FIT_DECREASING
  - Local search: LATE_ACCEPTANCE + TABU_SEARCH
  - Time limit: 120 seconds (configurable)
- [ ] Write tests: small problem (5 stops, 2 vehicles) with known optimal solution

**Depends on:** 5.1
**Definition of Done:** Solver produces valid routes respecting all hard constraints

---

### 5.3 Route Optimization Job & API

**Tasks:**
- [ ] Implement `RouteSolver`:
  - `optimize(distributorId, date)` → pulls orders, vehicles, computes matrix, runs solver, returns `RoutePlan`
  - `reoptimize(routeId, changes)` → adjusts existing route for disruptions (30-second time limit)
- [ ] Implement `RouteOptimizationJob` (Spring Batch):
  - Runs evening for next-day deliveries
  - Step 1: pull confirmed orders for tomorrow
  - Step 2: pull available vehicles and driver schedules
  - Step 3: compute distance matrix via `DistanceMatrixService`
  - Step 4: execute solver
  - Step 5: persist to `ai_delivery_routes`
- [ ] Create `AiRoutingController`:
  - `POST /v1/ai/routing/optimize` → trigger optimization (manual)
  - `GET /v1/ai/routing/routes/{date}` → get routes for a date
  - `POST /v1/ai/routing/reoptimize` → trigger intraday re-optimization
  - `GET /v1/ai/routing/routes/{routeId}` → get specific route detail
- [ ] Add Casbin RBAC: DISTRIBUTOR_ADMIN, DRIVER
- [ ] Add Prometheus metrics: solver duration, stops total, distance planned
- [ ] Write tests

**Depends on:** 5.2
**Definition of Done:** Evening route optimization runs automatically, routes queryable via API, re-optimization available

---

### Phase 5 Checkpoint

**Before moving to Phase 6, verify:**
- [ ] GraphHopper computing accurate Kenya road distances
- [ ] Solver producing valid, optimized routes
- [ ] Evening batch job running reliably
- [ ] Re-optimization working for intraday changes
- [ ] Routes accessible via API for driver mobile app
- [ ] Solver performance acceptable (< 2 minutes for typical distributor)

---

## Phase 6: AI Agent, Compliance Reporting & Credit Evolution

**Goal:** Deploy the operational recommendations agent, compliance reporting, dynamic credit adjustment, and begin the credit scoring evolution from LLM to ML.

**Estimated Duration:** 3-4 weeks

---

### 6.1 Agent Tools

**What:** Build the data retrieval tools the recommendation agent will use.

**Tasks:**
- [ ] Implement agent tools as Spring service methods with `@Tool` annotations:
  - `SalesTrendTool.getSalesTrend(distributorId, period)` → sales by territory, rep, product
  - `InventoryHealthTool.getInventoryHealth(distributorId)` → stock levels, turnover, aging
  - `PaymentPerformanceTool.getPaymentPerformance(distributorId)` → collection rates, aging, overdue
  - `RepPerformanceTool.getRepPerformance(distributorId)` → rep metrics and rankings
  - `MerchantMetricsTool.getMerchantMetrics(distributorId)` → acquisition, churn, activity
  - `AnomalyAlertsTool.getAnomalyAlerts(distributorId, period)` → recent alerts from Phase 4
  - `DeliveryMetricsTool.getDeliveryMetrics(distributorId)` → delivery success, cost per drop
- [ ] Each tool queries existing repositories and returns structured summary
- [ ] Each tool validates distributor authorization
- [ ] Write tests for each tool independently

**Depends on:** Existing repositories, 4.4 (AlertService)
**Definition of Done:** 7 agent tools independently tested, returning correct data

---

### 6.2 Recommendation Agent

**Tasks:**
- [ ] Implement `RecommendationAgent`:
  - LangChain4j Agent configuration with all 7 tools
  - System prompt: operational advisor role, investigation approach, output format
  - Max tool calls: 10 per run
  - LLM: cloud GPT-4/Claude primary (higher quality reasoning), Ollama fallback
  - Temperature: 0.4
- [ ] Define output structure:
  - List of `Recommendation` objects: observation, evidence (JSONB), recommendation, expected_impact, priority
- [ ] Implement `RecommendationJob`:
  - Scheduled weekly per distributor
  - Executes agent
  - Stores results in `ai_recommendations` table
  - Past recommendations embedded for RAG context in future runs
- [ ] Create `AiRecommendationController`:
  - `GET /v1/ai/recommendations/{distributorId}` → get recommendations
  - `PUT /v1/ai/recommendations/{recommendationId}/accept` → mark accepted
  - `PUT /v1/ai/recommendations/{recommendationId}/reject` → mark rejected
- [ ] Add Casbin RBAC: DISTRIBUTOR_ADMIN only
- [ ] Write tests (mock LLM responses to test tool orchestration)

**Depends on:** 6.1, 2.2
**Definition of Done:** Agent produces actionable recommendations using multiple data sources, accessible via API

---

### 6.3 Compliance Reporting

**Tasks:**
- [ ] Implement `ReportTemplateRegistry`:
  - Store prompt templates per principal (Unilever, P&G, EABL)
  - Each template: required sections, metrics, narrative tone, structure
  - Templates versioned in database
- [ ] Implement `ComplianceReportAiService` (LangChain4j AI Service):
  - Input: structured operational data + template
  - Output: report sections with narrative
  - LLM: Ollama primary, cloud fallback
  - Temperature: 0.3
- [ ] Implement `ComplianceReportJob`:
  - Scheduled monthly (or configurable)
  - Pulls data from existing report endpoints
  - Generates narrative via LLM
  - Stores for review before submission
- [ ] Create `AiReportController`:
  - `POST /v1/ai/reports/compliance/generate` → trigger generation
  - `GET /v1/ai/reports/compliance/{reportId}` → get generated report
- [ ] Add Casbin RBAC: DISTRIBUTOR_ADMIN
- [ ] Write tests

**Depends on:** 2.2
**Definition of Done:** Compliance reports generated with narrative sections, queryable via API

---

### 6.4 Dynamic Credit Limit Adjustment

**Tasks:**
- [ ] Implement `CreditLimitAdjuster`:
  - Algorithm: Tribuo `XGBoostRegressionTrainer`
  - Input: merchant performance features
  - Output: recommended credit limit (KES)
  - Training: on historical credit assignments and outcomes
- [ ] Implement business rules:
  - Max increase: 20% per period
  - Decrease: requires human review
  - Minimum floor: configurable per distributor
  - Auto-apply within bounds, route exceptions to finance team
- [ ] Create monthly batch job:
  - Compute features for all active merchants
  - Predict optimal limits
  - Apply rules
  - Auto-adjust or route for review
- [ ] Add to `AiCreditController`:
  - `POST /v1/ai/credit/adjust/{merchantId}` → trigger manual adjustment
- [ ] Write tests

**Depends on:** 1.4, 3.2, 2.6
**Definition of Done:** Credit limits adjusted monthly with appropriate guardrails

---

### 6.5 Credit Scoring Evolution — ML Classifier

**What:** Begin training the XGBoost credit classifier alongside the LLM scorer.

**Tasks:**
- [ ] Implement `CreditClassifier`:
  - Algorithm: `XGBoostClassificationTrainer` — binary classification (default within 90 days)
  - Input: numeric feature vector from `CreditFeatureBuilder.buildMlFeatureVector()`
  - Output: default probability (0-1), mapped to grade via thresholds (0-5% → A, 5-15% → B, 15-30% → C, 30-50% → D, 50%+ → F)
  - Class imbalance: `scale_pos_weight` parameter
- [ ] Create training job: monthly (runs only when sufficient default data exists — minimum 100 default events, 1000+ merchants)
- [ ] Implement hybrid scoring in `CreditScoringOrchestrator`:
  - If ML model exists and active: run both LLM and ML, compare results
  - Log agreement/disagreement for analysis
  - If both agree: use ML result (faster, cheaper)
  - If disagreement: route to human review with both evaluations
- [ ] Track ML model accuracy against actual outcomes monthly
- [ ] Write tests

**Depends on:** 2.5 (existing LLM scoring), 3.2
**Definition of Done:** ML classifier training when data sufficient, hybrid scoring operational, transition path validated

---

### 6.6 Payment Distress Classifier

**Tasks:**
- [ ] Implement `PaymentDistressClassifier`:
  - Algorithm: `XGBoostClassificationTrainer`
  - Input: merchant payment trend features
  - Output: probability of default within 90 days
  - Training: on historical defaults (requires 100+ events)
  - Conditional: only trains when sufficient data exists
- [ ] Integrate with `PaymentAnomalyDetector`:
  - Anomaly detector (Isolation Forest) catches novel patterns
  - Distress classifier catches known distress patterns
  - Either flagging creates alert via `AlertService`
- [ ] Write tests

**Depends on:** 4.2, 3.2
**Definition of Done:** Distress classifier supplements anomaly detection when sufficient training data exists

---

### 6.7 Drift Detection & Model Monitoring

**What:** Implement the monitoring layer for all models (Blueprint Section 11).

**Tasks:**
- [ ] Implement `DriftDetector`:
  - Computes Population Stability Index (PSI) per feature per model
  - Compares current feature distributions against training data distributions
  - Runs weekly
  - Threshold: PSI > 0.2 triggers retraining alert
- [ ] Implement `ModelPerformanceTracker`:
  - Credit scoring: track actual default rate per grade bracket
  - Demand forecasting: track predicted vs. actual quantities (MAPE)
  - Stockout prediction: track predicted vs. actual stockouts
  - Anomaly detection: track confirmed vs. dismissed alerts (false positive rate)
  - Stores in `ai_model_performance` table
- [ ] Implement prediction distribution monitoring:
  - Track weekly distribution of predictions per model
  - Alert if distribution shifts significantly
- [ ] Create Grafana dashboards:
  - Model Quality Dashboard: accuracy trends, drift indicators
  - Alert Dashboard: open alerts, resolution rates, false positive rates
- [ ] Write tests

**Depends on:** All previous phases (monitors everything)
**Definition of Done:** Model quality tracked continuously, drift detected, dashboards live

---

### Phase 6 Checkpoint

**Before considering the AI system complete, verify:**
- [ ] Recommendation agent produces useful, actionable insights
- [ ] Compliance reports generate correctly per principal template
- [ ] Credit limits adjust monthly with proper guardrails
- [ ] ML credit classifier training when data allows
- [ ] Hybrid credit scoring (LLM + ML) operational
- [ ] Payment distress classifier active (if data sufficient)
- [ ] Drift detection monitoring all models
- [ ] Model performance dashboards live
- [ ] All 12 AI use cases functional

---

## Final System Verification Checklist

### All 12 AI Capabilities Operational

- [ ] **1. Credit Risk Scoring** — LLM evaluation with business rules, evolving to ML classifier
- [ ] **2. Order Suggestions** — ML-powered suggestions for sales reps via API
- [ ] **3. Demand Forecasting** — Nightly batch forecasts at merchant-SKU level
- [ ] **4. Route Optimization** — Evening route planning, intraday re-optimization
- [ ] **5. Shrinkage Detection** — Real-time scoring on inventory adjustments
- [ ] **6. Payment Anomaly Detection** — Real-time scoring on payments + distress classification
- [ ] **7. Stockout Prediction** — Nightly predictions with alerts
- [ ] **8. Rep Underperformance** — Weekly monitoring with alerts
- [ ] **9. Dynamic Credit Adjustment** — Monthly limit adjustments with guardrails
- [ ] **10. Operational Recommendations** — Weekly agent-generated insights
- [ ] **11. Compliance Reporting** — Monthly auto-generated reports
- [ ] **12. Data Quality Detection** — Real-time rules + batch ML detection

### Infrastructure Verified

- [ ] Ollama running with local LLM, GPU utilized
- [ ] Cloud LLM fallback functional
- [ ] All ML models training on schedule
- [ ] Model registry tracking all versions
- [ ] Feature services computing correctly with caching
- [ ] Events triggering real-time AI scoring
- [ ] All API endpoints authorized and tested
- [ ] Prometheus metrics flowing
- [ ] Grafana dashboards operational
- [ ] Audit trail complete for all AI decisions

### Performance Verified

- [ ] Order suggestions API response < 500ms
- [ ] Credit scoring evaluation < 30 seconds
- [ ] Route optimization < 2 minutes for typical distributor
- [ ] Real-time anomaly scoring not impacting transaction latency
- [ ] Nightly batch jobs completing before business hours (6:00 AM EAT)
- [ ] ML model training completing within scheduled windows

### KCB Partnership Requirements Met

- [ ] Every credit decision logged with inputs, outputs, model version, reasoning
- [ ] Human override capability with audit trail
- [ ] Model versioning with performance history
- [ ] Data privacy: sensitive data processed locally via Ollama
- [ ] Kenya Data Protection Act compliance verified