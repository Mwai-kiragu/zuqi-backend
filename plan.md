# Zuqi AI Integration — System Architecture Blueprint

**Version:** 1.0
**Date:** February 2026
**Classification:** Internal Technical Architecture Document

---

## 1. System Overview

### 1.1 Purpose

This document defines the complete system architecture for integrating AI capabilities into the Zuqi field sales and distribution management platform. The AI layer enhances existing workflows for sales reps, merchants, distributors, warehouse managers, finance teams, and drivers — adding intelligence on top of data already flowing through the system.

### 1.2 Design Principles

- **Java-native.** The entire production runtime is Java. No Python microservices, no cross-language communication overhead, single deployment pipeline.
- **Modular.** Each AI capability is an independent module. Modules can be developed, deployed, and scaled independently.
- **Data-driven.** All AI features consume data already captured by existing Zuqi workflows. No new data collection required at launch.
- **Auditable.** Every AI decision is logged with inputs, outputs, model version, and confidence score. Required for the KCB banking partnership.
- **Evolvable.** Architecture supports migrating from LLM-based solutions to trained ML models as data accumulates.

### 1.3 AI Capability Summary

| # | Capability | AI Type | Primary Technology |
|---|---|---|---|
| 1 | Credit Risk Scoring | LLM Chain → ML Classifier | LangChain4j → Tribuo |
| 2 | AI-Powered Order Suggestions | ML Regression + optional LLM | Tribuo + LangChain4j |
| 3 | Demand Forecasting | ML Regression | Tribuo (XGBoost) |
| 4 | Dynamic Route Optimization | Constraint Solver | Timefold + GraphHopper |
| 5 | Inventory Shrinkage Detection | ML Anomaly Detection | Tribuo (Isolation Forest) |
| 6 | Payment Anomaly Detection | ML Anomaly + Classification | Tribuo |
| 7 | Stockout Prediction | ML Classification | Tribuo (XGBoost) |
| 8 | Sales Rep Underperformance | ML Regression | Tribuo (XGBoost) |
| 9 | Dynamic Credit Limit Adjustment | ML Regression + Rules | Tribuo (XGBoost) |
| 10 | Operational Recommendations | AI Agent | LangChain4j Agent |
| 11 | Compliance Reporting | LLM Chain | LangChain4j AI Service |
| 12 | Data Quality Detection | Rules + ML | Java Rules + Tribuo |

---

## 2. Technology Stack

### 2.1 Core AI Technologies

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| LLM Framework | LangChain4j | Latest stable | LLM chains, agents, RAG, structured output |
| Local LLM Runtime | Ollama | Latest stable | Hosts local open-source LLMs |
| Local LLM Model | Qwen 2.5 32B / Mixtral 8x7B | Latest | Credit scoring, suggestions, reporting |
| Cloud LLM (fallback) | GPT-4 / Claude API | Latest | Complex agent reasoning, fallback |
| Classical ML | Tribuo (Oracle) | Latest stable | XGBoost, Isolation Forest, regression, classification |
| Optimization Solver | Timefold (OptaPlanner) | Latest stable | Vehicle routing optimization |
| Road Network | GraphHopper | Latest stable | Distance/time matrix for Kenya road network |
| Model Portability | ONNX Runtime for Java | Latest stable | Fallback: import externally trained models |

### 2.2 Supporting Infrastructure

| Layer | Technology | Purpose |
|---|---|---|
| Embedding Storage | PostgreSQL + pgvector | RAG context retrieval, similarity search |
| Feature Caching | Redis | Cache computed features and predictions |
| Batch Scheduling | Spring Batch + @Scheduled | Training pipelines, nightly forecasts, re-evaluations |
| Event Bus | Spring ApplicationEventPublisher (initial) → RabbitMQ/Kafka (at scale) | Real-time scoring triggers |
| Model Registry | PostgreSQL (custom tables) | Model versioning, performance tracking, audit trails |
| Monitoring | Prometheus + Grafana | Model performance, drift detection, system health |
| GPU Infrastructure | NVIDIA A10G or L4 (24GB VRAM) | Local LLM inference |

### 2.3 Integration with Existing Project

The AI layer lives inside the existing single-module project as the `com.zuqi.ai` package. No build restructuring required — AI code, entities, and controllers are added alongside existing code with clean package separation.

```
zuqi-backend/
├── pom.xml                              (existing — add AI dependencies here)
└── src/main/java/com/zuqi/
    │
    ├── ZuqiApplication.java             (existing — no changes needed)
    ├── api/                             (existing 14 controllers, 60+ DTOs)
    ├── config/                          (existing 6 config classes)
    ├── domain/                          (existing 30+ JPA entities)
    ├── exception/                       (existing exception handling)
    ├── repository/                      (existing 21 repositories)
    ├── security/                        (existing JWT + Casbin)
    ├── service/                         (existing 15 service implementations)
    ├── util/                            (existing utilities)
    │
    └── ai/                              (NEW — all AI code here)
        ├── config/
        ├── feature/
        ├── model/
        ├── credit/
        ├── demand/
        ├── anomaly/
        ├── prediction/
        ├── routing/
        ├── agent/
        ├── reporting/
        ├── pipeline/
        └── monitoring/
```

**Why single module with package separation:**
- **Zero build complexity:** no multi-module Maven issues, single `pom.xml`, single build command
- **Direct access:** AI services directly inject existing repositories, entities, and services via standard Spring DI
- **Simple debugging:** no cross-module boundary issues
- **Clean enough:** `com.zuqi.ai.*` is clearly separated from `com.zuqi.api.*`, `com.zuqi.service.*`, etc.
- **Future-proof:** can extract into separate module later if the project grows to 500+ files

**Integration points:**
- AI dependencies (LangChain4j, Tribuo, Timefold, GraphHopper) added to existing `pom.xml`
- AI REST controllers in `com.zuqi.ai` auto-discovered by Spring component scanning (already under `com.zuqi` base package)
- Flyway migrations for AI tables continue the existing sequence (V15+) in `src/main/resources/db/migration/`
- Casbin policies for AI endpoints added to existing `policy.csv`
- AI configuration properties added to existing `application.yml`
- AI domain entities and repositories live under `com.zuqi.ai` package, separate from core entities

---

## 3. Module Architecture

### 3.1 Package Structure

```
com.zuqi/
├── ai/
│   ├── config/                    # AI module configuration
│   │   ├── LangChain4jConfig      # LLM provider configuration (Ollama/OpenAI)
│   │   ├── TribuoConfig            # ML model loader and registry config
│   │   ├── TimefoldConfig           # Solver configuration
│   │   └── GraphHopperConfig        # Road network configuration
│   │
│   ├── feature/                    # Feature engineering services
│   │   ├── MerchantFeatureService   # Computes merchant-level features
│   │   ├── OrderFeatureService      # Computes order/demand features
│   │   ├── PaymentFeatureService    # Computes payment behavior features
│   │   ├── InventoryFeatureService  # Computes inventory/stock features
│   │   ├── SalesRepFeatureService   # Computes sales rep performance features
│   │   └── FeatureStore             # Centralized feature access and caching
│   │
│   ├── model/                      # ML model management
│   │   ├── ModelRegistry            # Model versioning and metadata
│   │   ├── ModelTrainer             # Generic training pipeline orchestrator
│   │   ├── ModelEvaluator           # Model performance evaluation
│   │   └── ModelLoader              # Loads active models for inference
│   │
│   ├── credit/                     # Credit risk AI module
│   │   ├── CreditScoringAiService   # LangChain4j AI Service interface
│   │   ├── CreditClassifier         # Tribuo XGBoost classifier (Phase 2+)
│   │   ├── CreditFeatureBuilder     # Builds credit-specific feature vectors
│   │   ├── CreditExplainer          # LLM-based explanation generator
│   │   └── CreditLimitAdjuster      # Dynamic limit adjustment logic
│   │
│   ├── demand/                     # Demand forecasting module
│   │   ├── DemandForecaster         # Tribuo XGBoost regression model
│   │   ├── DemandFeatureBuilder     # Builds demand-specific feature vectors
│   │   ├── DemandForecastJob        # Spring Batch nightly forecast job
│   │   └── OrderSuggestionService   # Generates sales rep suggestions
│   │
│   ├── anomaly/                    # Anomaly detection module
│   │   ├── ShrinkageDetector        # Tribuo Isolation Forest for inventory
│   │   ├── PaymentAnomalyDetector   # Tribuo Isolation Forest for payments
│   │   ├── PaymentDistressClassifier # Tribuo XGBoost for distress (Phase 2+)
│   │   ├── DataQualityDetector      # Rules + Tribuo for data quality
│   │   ├── AnomalyFeatureBuilder    # Builds anomaly-specific features
│   │   └── AlertService             # Alert generation, deduplication, delivery
│   │
│   ├── prediction/                 # Predictive alerts module
│   │   ├── StockoutPredictor        # Tribuo XGBoost classifier
│   │   ├── RepPerformancePredictor  # Tribuo XGBoost regression
│   │   └── PredictionAlertService   # Threshold evaluation and alert routing
│   │
│   ├── routing/                    # Route optimization module
│   │   ├── RouteSolver              # Timefold solver wrapper
│   │   ├── DistanceMatrixService    # GraphHopper distance/time computation
│   │   ├── RouteOptimizationJob     # Evening batch route planning
│   │   └── domain/                  # Timefold planning entities
│   │       ├── Vehicle               # Planning entity: delivery vehicle
│   │       ├── DeliveryStop           # Planning entity: merchant delivery
│   │       └── RoutePlan              # Planning solution
│   │
│   ├── agent/                      # AI Agent module
│   │   ├── RecommendationAgent      # LangChain4j Agent for operational insights
│   │   ├── tools/                   # Agent tools (callable by LLM)
│   │   │   ├── SalesTrendTool
│   │   │   ├── InventoryHealthTool
│   │   │   ├── PaymentPerformanceTool
│   │   │   ├── RepPerformanceTool
│   │   │   ├── MerchantMetricsTool
│   │   │   ├── AnomalyAlertsTool
│   │   │   └── DeliveryMetricsTool
│   │   └── RecommendationJob        # Scheduled weekly recommendation run
│   │
│   ├── reporting/                  # AI-powered reporting module
│   │   ├── ComplianceReportAiService # LangChain4j AI Service for reports
│   │   ├── ReportTemplateRegistry    # Principal-specific prompt templates
│   │   └── ComplianceReportJob       # Scheduled report generation
│   │
│   ├── pipeline/                   # Training pipeline orchestration
│   │   ├── TrainingPipelineJob      # Spring Batch master training job
│   │   ├── FeatureComputationStep   # Batch step: compute all features
│   │   ├── ModelTrainingStep        # Batch step: train models
│   │   ├── ModelEvaluationStep      # Batch step: evaluate on test set
│   │   ├── ModelPromotionStep       # Batch step: promote if metrics pass
│   │   └── DriftDetectionStep       # Batch step: check for data drift
│   │
│   └── monitoring/                 # AI system monitoring
│       ├── PredictionLogger         # Logs all predictions for audit
│       ├── DriftDetector            # Detects feature and prediction drift
│       ├── ModelPerformanceTracker   # Tracks accuracy over time
│       └── AIHealthEndpoint         # REST endpoint for AI system health
```

### 3.2 Module Dependencies

```
Feature Engineering Layer (foundation — all modules depend on this)
    │
    ├── credit/        ← feature/, model/
    ├── demand/        ← feature/, model/
    ├── anomaly/       ← feature/, model/
    ├── prediction/    ← feature/, model/, demand/ (stockout depends on forecasts)
    ├── routing/       ← (independent — uses order data directly)
    ├── agent/         ← credit/, demand/, anomaly/, prediction/ (reads from all)
    ├── reporting/     ← (independent — uses operational data directly)
    │
    Model Registry (model/) ← all ML modules register and load models through this
    │
    Training Pipeline (pipeline/) ← orchestrates feature/, model/, and all ML modules
    │
    Monitoring (monitoring/) ← observes all modules
```

---

## 4. Feature Engineering Layer

### 4.1 Purpose

The feature engineering layer is the foundation of the entire AI system. It computes derived features from raw operational data that all ML models and LLM prompts consume. Centralizing feature logic ensures consistency between training and inference — the single most important requirement for reliable ML.

### 4.2 Feature Services

#### MerchantFeatureService

Computes features for a single merchant from existing domain entities:

**Order features:** total_orders, order_frequency_per_week, avg_order_value, order_value_trend_slope_12w, order_consistency_stddev, cancellation_rate, return_rate, days_since_last_order, unique_skus_ordered, top_sku_concentration

**Payment features:** total_payments, on_time_payment_pct, avg_days_to_pay, worst_days_to_pay, partial_payment_frequency, payment_method_distribution (JSON), consecutive_on_time_streak, total_overdue_amount

**Credit features:** current_credit_limit, current_utilization_ratio, peak_utilization_ratio, utilization_trend_slope, limit_increase_count, days_since_last_limit_change

**Profile features:** business_category (encoded), relationship_tenure_days, verification_status, geographic_cluster

**Data sources:** Order entity, Payment entity, Merchant entity, CreditLimit entity — all existing JPA entities

#### OrderFeatureService (Demand Features)

Computes features for a merchant-SKU combination:

**Lag features:** qty_1w_ago, qty_2w_ago, qty_3w_ago, qty_4w_ago, rolling_avg_4w, rolling_avg_12w, trend_direction (short_avg - long_avg)

**Temporal features:** day_of_week, week_of_month, month_of_year, is_holiday, is_payday_week, is_ramadan, is_christmas_season

**Merchant context:** merchant_category, merchant_size_tier, merchant_credit_status, merchant_tenure

**SKU context:** product_category, price_tier, is_promotional, typical_shelf_life

**Data sources:** Order entity, OrderItem entity, Product entity, Merchant entity

#### PaymentFeatureService

Computes features for payment anomaly detection:

**Per-payment features:** days_to_payment_vs_merchant_avg, amount_vs_invoice_amount_ratio, payment_method_encoded, hour_of_day, is_partial, gap_since_last_payment_days

**Merchant trend features:** days_to_pay_trend_3m, order_frequency_trend_3m, credit_utilization_trajectory, partial_payment_freq_trend, avg_order_value_trend

**Data sources:** Payment entity, Invoice entity, Order entity, Merchant entity

#### InventoryFeatureService

Computes features for shrinkage and stockout detection:

**Per warehouse-SKU:** current_stock, expected_stock (opening + in - out), discrepancy, discrepancy_pct, manual_adjustment_count_7d, adjustment_time_distribution, adjusting_user_ids, consumption_rate_7d, consumption_rate_30d, consumption_trend, pending_reserved_qty, expected_incoming_qty

**Data sources:** StockMovement entity, Inventory entity, Warehouse entity, Order entity

#### SalesRepFeatureService

Computes features for rep performance prediction:

**Per-rep per-period:** visit_count_vs_target, order_conversion_rate, total_order_value, avg_order_value, new_merchants_acquired, collection_rate, route_adherence_pct, territory_penetration_pct

**Data sources:** Order entity, Merchant entity, User entity (sales rep), Visit entity (if tracked)

### 4.3 FeatureStore

Centralized access point for all features with caching:

- **Computes or retrieves** cached features via Redis
- **TTL-based caching** — merchant features cached for 24 hours, demand features refreshed nightly, payment features refreshed on each payment event
- **Training mode** — computes historical features for a date range (for model training)
- **Inference mode** — computes current features for a single entity (for real-time scoring)
- **Consistency guarantee** — both training and inference code paths call the same feature computation methods

---

## 5. ML Model Management

### 5.1 Model Registry (Database Schema)

```sql
-- Model metadata and versioning
CREATE TABLE ai_model_registry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name      VARCHAR(100) NOT NULL,     -- e.g., 'demand_forecaster', 'shrinkage_detector'
    model_version   INTEGER NOT NULL,
    algorithm       VARCHAR(50) NOT NULL,       -- e.g., 'xgboost_regression', 'isolation_forest'
    status          VARCHAR(20) NOT NULL,       -- TRAINING, EVALUATING, ACTIVE, RETIRED
    distributor_id  UUID REFERENCES distributors(id) ON DELETE CASCADE,  -- Models can be distributor-specific
    training_data_start TIMESTAMP,
    training_data_end   TIMESTAMP,
    training_record_count INTEGER,
    performance_metrics JSONB,                  -- {"mae": 2.3, "rmse": 4.1, "mape": 0.12}
    hyperparameters     JSONB,                  -- {"max_depth": 6, "learning_rate": 0.1}
    model_binary        BYTEA,                  -- Serialized Tribuo model
    model_size_bytes    BIGINT,
    feature_columns     JSONB,                  -- Ordered list of feature names
    created_at      TIMESTAMP DEFAULT NOW(),
    created_by      VARCHAR(100),
    promoted_at     TIMESTAMP,                  -- When moved to ACTIVE
    retired_at      TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(model_name, model_version)
);

-- Indexes for quick model lookup
CREATE INDEX idx_model_active ON ai_model_registry(model_name, status)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_model_registry_created ON ai_model_registry(created_at DESC);
CREATE INDEX idx_model_registry_distributor ON ai_model_registry(distributor_id) WHERE distributor_id IS NOT NULL;

-- Individual prediction audit log
CREATE TABLE ai_predictions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name      VARCHAR(100) NOT NULL,
    model_version   INTEGER NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,       -- 'merchant', 'warehouse_sku', 'sales_rep'
    entity_id       UUID NOT NULL,
    distributor_id  UUID REFERENCES distributors(id) ON DELETE CASCADE,  -- Multi-tenant filtering
    input_features_hash VARCHAR(64),            -- SHA-256 of input features
    prediction_value    JSONB NOT NULL,          -- {"grade": "B", "limit": 85000} or {"quantity": 24}
    confidence_score    DOUBLE PRECISION,
    was_overridden      BOOLEAN DEFAULT FALSE,
    override_value      JSONB,
    override_by         VARCHAR(100),
    override_reason     TEXT,
    expires_at      TIMESTAMP,                  -- Data retention / GDPR compliance
    created_at      TIMESTAMP DEFAULT NOW()
);

-- Indexes for prediction lookups
CREATE INDEX idx_predictions_entity ON ai_predictions(entity_type, entity_id, created_at DESC);
CREATE INDEX idx_predictions_model ON ai_predictions(model_name, model_version);
CREATE INDEX idx_predictions_distributor ON ai_predictions(distributor_id);
CREATE INDEX idx_predictions_created ON ai_predictions(created_at DESC);

-- Model performance tracking over time
CREATE TABLE ai_model_performance (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name      VARCHAR(100) NOT NULL,
    model_version   INTEGER NOT NULL,
    evaluation_date DATE NOT NULL,
    metric_name     VARCHAR(50) NOT NULL,       -- 'accuracy', 'precision', 'recall', 'mae', 'rmse'
    metric_value    DOUBLE PRECISION NOT NULL,
    sample_size     INTEGER,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(model_name, model_version, evaluation_date, metric_name)  -- Prevent duplicate metrics
);

-- Indexes for performance tracking
CREATE INDEX idx_model_performance_lookup ON ai_model_performance(model_name, model_version, evaluation_date);
CREATE INDEX idx_model_performance_metric ON ai_model_performance(metric_name);

-- Demand forecasts (consumed by stockout prediction and order suggestions)
CREATE TABLE ai_demand_forecasts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    sku_id          UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    forecast_date   DATE NOT NULL,
    predicted_qty   DOUBLE PRECISION NOT NULL,
    confidence_lower DOUBLE PRECISION,
    confidence_upper DOUBLE PRECISION,
    model_version   INTEGER NOT NULL,
    expires_at      TIMESTAMP,                  -- Data retention
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(merchant_id, sku_id, forecast_date)
);

-- Indexes for forecast lookups
CREATE INDEX idx_demand_forecasts_merchant ON ai_demand_forecasts(merchant_id);
CREATE INDEX idx_demand_forecasts_date ON ai_demand_forecasts(forecast_date);
CREATE INDEX idx_demand_forecasts_distributor ON ai_demand_forecasts(distributor_id);

-- Anomaly alerts
CREATE TABLE ai_anomaly_alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_type      VARCHAR(50) NOT NULL,       -- 'shrinkage', 'payment_anomaly', 'data_quality'
    severity        VARCHAR(20) NOT NULL,       -- 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    anomaly_score   DOUBLE PRECISION,
    description     TEXT NOT NULL,
    context         JSONB,                      -- Supporting data for investigation
    status          VARCHAR(20) DEFAULT 'OPEN', -- 'OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'DISMISSED'
    resolved_by     VARCHAR(100),
    resolved_at     TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Indexes for alert management
CREATE INDEX idx_anomaly_alerts_distributor ON ai_anomaly_alerts(distributor_id);
CREATE INDEX idx_anomaly_alerts_status ON ai_anomaly_alerts(status);
CREATE INDEX idx_anomaly_alerts_severity ON ai_anomaly_alerts(severity);
CREATE INDEX idx_anomaly_alerts_type ON ai_anomaly_alerts(alert_type);
CREATE INDEX idx_anomaly_alerts_entity ON ai_anomaly_alerts(entity_type, entity_id);
CREATE INDEX idx_anomaly_alerts_created ON ai_anomaly_alerts(created_at DESC);

-- Operational recommendations
CREATE TABLE ai_recommendations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    recommendation_type VARCHAR(50) NOT NULL,
    observation     TEXT NOT NULL,
    evidence        JSONB NOT NULL,
    recommendation  TEXT NOT NULL,
    expected_impact TEXT,
    priority        VARCHAR(20) NOT NULL,       -- 'LOW', 'MEDIUM', 'HIGH'
    status          VARCHAR(20) DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'REJECTED', 'COMPLETED'
    acted_on_at     TIMESTAMP,
    outcome         TEXT,
    model_version   VARCHAR(50),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Indexes for recommendation management
CREATE INDEX idx_recommendations_distributor ON ai_recommendations(distributor_id);
CREATE INDEX idx_recommendations_status ON ai_recommendations(status);
CREATE INDEX idx_recommendations_priority ON ai_recommendations(priority);
CREATE INDEX idx_recommendations_type ON ai_recommendations(recommendation_type);
CREATE INDEX idx_recommendations_created ON ai_recommendations(created_at DESC);

-- Delivery routes
CREATE TABLE ai_delivery_routes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    route_date      DATE NOT NULL,
    vehicle_id      UUID,                       -- Vehicle info stored in metadata for now (vehicles table TBD)
    vehicle_info    JSONB,                      -- {capacity_kg, capacity_volume, registration, type}
    driver_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stop_sequence   JSONB NOT NULL,             -- Ordered list of {merchant_id, eta, order_ids}
    total_distance_km   DOUBLE PRECISION,
    total_duration_min  DOUBLE PRECISION,
    load_utilization_pct DOUBLE PRECISION,
    solver_time_ms      INTEGER,
    status          VARCHAR(20) DEFAULT 'PLANNED', -- 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
    actual_distance_km  DOUBLE PRECISION,       -- Filled after completion
    actual_duration_min DOUBLE PRECISION,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Indexes for route management
CREATE INDEX idx_delivery_routes_distributor ON ai_delivery_routes(distributor_id);
CREATE INDEX idx_delivery_routes_date ON ai_delivery_routes(route_date);
CREATE INDEX idx_delivery_routes_driver ON ai_delivery_routes(driver_id);
CREATE INDEX idx_delivery_routes_status ON ai_delivery_routes(status);
CREATE INDEX idx_delivery_routes_created ON ai_delivery_routes(created_at DESC);

-- Merchant embeddings for RAG (credit scoring similarity)
CREATE TABLE ai_merchant_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    embedding       vector(768),                -- Dimension depends on embedding model
    feature_summary TEXT,                       -- Human-readable summary used for embedding
    model_version   VARCHAR(50),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(merchant_id)
);

-- pgvector index for similarity search (cosine distance)
CREATE INDEX idx_merchant_embeddings_similarity ON ai_merchant_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Recommendation embeddings for RAG (agent context)
CREATE TABLE ai_recommendation_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id UUID NOT NULL REFERENCES ai_recommendations(id) ON DELETE CASCADE,
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    embedding       vector(768),
    recommendation_summary TEXT,
    model_version   VARCHAR(50),
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(recommendation_id)
);

-- pgvector index for similarity search
CREATE INDEX idx_recommendation_embeddings_similarity ON ai_recommendation_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

### 5.2 Model Lifecycle

```
TRAINING → EVALUATING → ACTIVE → RETIRED

1. TRAINING:   Model is being trained on latest data
2. EVALUATING: Model is evaluated against held-out test set
3. ACTIVE:     Model passed quality gates, serving predictions
               (only one ACTIVE version per model_name at a time)
4. RETIRED:    Replaced by newer version, kept for audit purposes
```

### 5.3 ModelLoader

- On application startup, loads all ACTIVE models from the registry into memory
- Models are held as Tribuo `Model<>` objects in a `ConcurrentHashMap<String, Model<?>>`
- When a new model is promoted to ACTIVE, it's hot-swapped without application restart
- Fallback: if no ACTIVE model exists for a given name, the module gracefully degrades (returns no prediction rather than failing)

---

## 6. Detailed Module Specifications

### 6.1 Credit Risk Module (`ai/credit/`)

#### Components

**CreditScoringAiService** (LangChain4j AI Service)
- LangChain4j `@AiService` interface
- Input: `MerchantCreditProfile` (computed by `CreditFeatureBuilder`)
- Peer context: similar merchants retrieved via pgvector embedding similarity
- Output: `CreditEvaluation` POJO — grade (A-F), recommended_limit (KES), confidence (0-1), risk_factors (List<String>), reasoning (String)
- LLM provider: Ollama (Qwen 2.5 32B) primary, cloud GPT-4/Claude fallback
- Temperature: 0.1 (low for consistency)
- Protected by Resilience4j circuit breaker

**CreditClassifier** (Tribuo XGBoost — Phase 2+)
- Algorithm: `XGBoostClassificationTrainer` — binary classification
- Input: numeric feature vector from `CreditFeatureBuilder`
- Output: default probability (0-1), mapped to grade via configurable thresholds
- Training data: merchants with known outcomes (defaulted vs. reliable)
- Class imbalance: handled via `scale_pos_weight` parameter
- Minimum training requirement: 100+ default events across 1000+ merchants
- Replaces `CreditScoringAiService` as primary scorer when sufficient data exists

**CreditFeatureBuilder**
- Calls `MerchantFeatureService` to gather raw features
- Produces two outputs: structured `MerchantCreditProfile` for LLM consumption, numeric `Example<Label>` for Tribuo model consumption
- Same data, different formats — ensures LLM and ML model evaluate the same information

**CreditExplainer** (LangChain4j AI Service)
- Generates human-readable explanation of ML model decisions (Phase 2+)
- Input: merchant features + ML model prediction + feature importance scores
- Output: narrative explanation for credit officers

**CreditLimitAdjuster**
- Algorithm: `XGBoostRegressionTrainer`
- Runs monthly for all active merchants
- Predicts optimal credit limit based on current performance
- Business rules constrain output: max 20% increase per period, decreases require human review, minimum floor enforced
- Auto-applies adjustments within bounds, routes exceptions to finance team

#### Trigger Points
- Merchant onboarding → `CreditScoringAiService`
- Credit limit increase request → `CreditScoringAiService`
- Monthly scheduled re-evaluation → `CreditScoringAiService` or `CreditClassifier`
- Monthly adjustment cycle → `CreditLimitAdjuster`
- On each order placement → credit utilization check (existing logic, no AI)

---

### 6.2 Demand Forecasting Module (`ai/demand/`)

#### Components

**DemandForecaster**
- Algorithm: `XGBoostRegressionTrainer`
- Input: feature vector per merchant-SKU combination from `DemandFeatureBuilder`
- Output: predicted order quantity for next period
- Trained on: 6-12 months of historical order data
- Retrained: weekly

**DemandFeatureBuilder**
- Calls `OrderFeatureService` for lag and temporal features
- Calls `MerchantFeatureService` for merchant context
- Produces: Tribuo `Example<Regressor>` for training, feature vector for inference
- Historical mode: computes features as they would have existed at a past date (for training)
- Current mode: computes features as of now (for inference)

**DemandForecastJob** (Spring Batch)
- Runs nightly
- Step 1: compute features for all active merchant-SKU combinations
- Step 2: run inference through `DemandForecaster`
- Step 3: store predictions in `ai_demand_forecasts` table
- Step 4: aggregate to warehouse-SKU level for inventory planning
- Forecast horizon: 7 days forward

**OrderSuggestionService**
- Called when sales rep opens app at merchant location
- Retrieves pre-computed forecasts from `ai_demand_forecasts`
- Filters: removes out-of-stock SKUs, enforces credit limit
- Ranks: by confidence, margin contribution, recency
- Optional LLM enrichment: brief sales tips via LangChain4j
- Returns: ordered list of `OrderSuggestion` DTOs to mobile API

#### Trigger Points
- Nightly batch → `DemandForecastJob`
- Sales rep merchant visit → `OrderSuggestionService`
- Weekly → model retraining pipeline

---

### 6.3 Anomaly Detection Module (`ai/anomaly/`)

#### Components

**ShrinkageDetector**
- Algorithm: Tribuo `IsolationForestTrainer`
- Input: inventory discrepancy features from `InventoryFeatureService`
- Output: anomaly score (0-1), flagged if above threshold
- Trained on: 3 months of inventory movement data
- Retrained: weekly

**PaymentAnomalyDetector**
- Algorithm: Tribuo `IsolationForestTrainer`
- Input: per-payment features from `PaymentFeatureService`
- Output: anomaly score (0-1)
- Trained on: historical payment patterns per merchant
- Retrained: weekly

**PaymentDistressClassifier** (Phase 2+ — requires default history)
- Algorithm: `XGBoostClassificationTrainer`
- Input: merchant payment trend features from `PaymentFeatureService`
- Output: probability of default within 90 days
- Training requirement: 100+ default events
- Retrained: monthly

**DataQualityDetector**
- Tier 1 (rules): Java validation logic applied on every data entry
  - Order quantity > 10x merchant average → flag
  - Coordinates > 50km from registered address → flag
  - Future-dated invoices → flag
  - Price override > 30% deviation → flag
  - Duplicate orders within 5-minute window → flag
- Tier 2 (ML): Tribuo `IsolationForestTrainer` on data entry patterns
  - Catches combinations that are individually acceptable but collectively suspicious
  - Retrained: weekly

**AnomalyFeatureBuilder**
- Shared feature building for all anomaly models
- Calls `InventoryFeatureService`, `PaymentFeatureService`, `OrderFeatureService`

**AlertService**
- Receives anomaly detections from all detectors
- Deduplicates: same entity + same alert type within 24 hours → update, don't create new
- Severity classification: based on anomaly score thresholds (configurable per alert type)
- Persists to `ai_anomaly_alerts` table
- Delivers: dashboard notification, high-severity alerts trigger push notifications

#### Trigger Points
- Stock adjustment recorded → `ShrinkageDetector` (event-driven)
- Payment recorded → `PaymentAnomalyDetector` (event-driven)
- Order created → `DataQualityDetector` Tier 1 (synchronous validation)
- Nightly batch → `DataQualityDetector` Tier 2, `PaymentDistressClassifier`

---

### 6.4 Predictive Alerts Module (`ai/prediction/`)

#### Components

**StockoutPredictor**
- Algorithm: `XGBoostClassificationTrainer`
- Input: warehouse-SKU features from `InventoryFeatureService` + demand forecasts from `ai_demand_forecasts`
- Output: stockout probability within 3, 5, and 7 days
- Dependency: requires `DemandForecaster` output
- Retrained: weekly

**RepPerformancePredictor**
- Algorithm: `XGBoostRegressionTrainer`
- Input: sales rep features from `SalesRepFeatureService`
- Output: expected performance score
- Underperformance detection: actual performance < expected - threshold for 2+ consecutive periods
- Retrained: monthly

**PredictionAlertService**
- Evaluates prediction outputs against configurable thresholds
- Stockout probability > 70% → generate alert with predicted date and recommended reorder quantity
- Rep performance gap > 20% for 2+ weeks → generate alert
- Routes alerts through `AlertService`

#### Trigger Points
- Nightly batch (after demand forecast) → `StockoutPredictor`
- Weekly batch → `RepPerformancePredictor`

---

### 6.5 Route Optimization Module (`ai/routing/`)

#### Components

**RouteSolver**
- Technology: Timefold solver
- Problem type: Vehicle Routing Problem with Time Windows (VRPTW)
- Hard constraints: vehicle capacity, driver max hours, all orders delivered
- Soft constraints: minimize total distance, minimize total time, balance driver workload, respect delivery windows
- Solver time limit: 120 seconds for evening planning, 30 seconds for intraday re-optimization
- Algorithms: construction heuristic (first fit decreasing) → late acceptance → tabu search

**Planning Domain Entities:**
- `Vehicle`: capacity_kg, capacity_volume, start_location (warehouse), driver_id, max_hours
- `DeliveryStop`: merchant_location (lat/lng), order_weight, order_volume, time_window_start, time_window_end, priority
- `RoutePlan`: @PlanningSolution — assigns stops to vehicles and sequences them

**DistanceMatrixService**
- Technology: GraphHopper (embedded Java library)
- Loaded with: OpenStreetMap data for Kenya
- Computes: travel time and distance between all location pairs
- Caching: Redis cache for frequently used location pairs
- Refresh: OSM data updated monthly

**RouteOptimizationJob** (Spring Batch)
- Runs: evening for next-day deliveries
- Step 1: pull confirmed orders for tomorrow
- Step 2: pull available vehicles and driver schedules
- Step 3: compute/retrieve distance matrix
- Step 4: execute solver
- Step 5: persist routes to `ai_delivery_routes`
- Step 6: push routes to driver mobile app

#### Trigger Points
- Evening scheduled job → next-day route planning
- Manual trigger → intraday re-optimization (failed delivery, urgent order)

---

### 6.6 AI Agent Module (`ai/agent/`)

#### Components

**RecommendationAgent**
- Technology: LangChain4j Agent with tool-use capability
- LLM: Ollama (Qwen 2.5 32B) or cloud GPT-4/Claude for higher quality
- Max tool calls per run: 10 (prevents runaway costs)
- System prompt: defines role as operational advisor for FMCG distributor

**Agent Tools** (Java methods exposed to LLM via LangChain4j `@Tool` annotation):
- `getSalesTrend(distributorId, period)` → sales data by territory, rep, product
- `getInventoryHealth(distributorId)` → stock levels, turnover, aging
- `getPaymentPerformance(distributorId)` → collection rates, aging, overdue
- `getRepPerformance(distributorId)` → rep metrics and rankings
- `getMerchantMetrics(distributorId)` → acquisition, churn, activity rates
- `getAnomalyAlerts(distributorId, period)` → recent alerts from anomaly detection
- `getDeliveryMetrics(distributorId)` → delivery success, cost per drop, route efficiency

Each tool is a Spring service method that queries existing repositories and aggregates data.

**RecommendationJob** (Spring Scheduled)
- Runs: weekly per distributor (or on-demand from dashboard)
- Executes agent, stores structured recommendations in `ai_recommendations` table
- Past recommendations and outcomes stored for RAG context in future runs

#### Output Structure
Each recommendation contains:
- `observation`: what the agent found (e.g., "Sales in Westlands territory dropped 18% over 3 weeks")
- `evidence`: supporting data points
- `recommendation`: specific action to take
- `expected_impact`: projected outcome if recommendation is followed
- `priority`: HIGH / MEDIUM / LOW

#### Trigger Points
- Weekly scheduled job
- On-demand from distributor dashboard

---

### 6.7 Compliance Reporting Module (`ai/reporting/`)

#### Components

**ComplianceReportAiService** (LangChain4j AI Service)
- Input: structured operational data for reporting period + principal-specific template
- Output: report sections with narrative text around structured data
- LLM: Ollama local model
- Temperature: 0.3 (some creativity for natural language, but grounded in data)

**ReportTemplateRegistry**
- Stores prompt templates per principal (Unilever, P&G, EABL, etc.)
- Each template defines: required data sections, narrative tone, specific metrics required, report structure
- Templates are versioned and updated when principal requirements change

**ComplianceReportJob** (Spring Scheduled)
- Runs: monthly or as configured per principal
- Pulls operational data from existing report endpoints
- Generates narrative via LLM
- Stores report for review before submission

#### Trigger Points
- Monthly scheduled job
- On-demand generation from dashboard

---

## 7. Training Pipeline Architecture

### 7.1 Pipeline Overview

All ML models follow the same training pipeline, orchestrated by Spring Batch:

```
FeatureComputationStep → ModelTrainingStep → ModelEvaluationStep → ModelPromotionStep
```

### 7.2 Pipeline Steps

**FeatureComputationStep**
- Computes historical features for the training date range
- Uses the same feature service methods as inference (consistency guarantee)
- Stores computed feature matrix temporarily (in-memory for small datasets, PostgreSQL temp table for large)
- Splits data: 80% training, 20% test (time-based split — train on older data, test on recent)

**ModelTrainingStep**
- Creates Tribuo `Trainer` with configured hyperparameters
- Trains model on training split
- Serializes trained model

**ModelEvaluationStep**
- Evaluates model on held-out test split
- Computes metrics appropriate to model type:
  - Regression: MAE, RMSE, MAPE, R²
  - Classification: accuracy, precision, recall, F1, AUC-ROC
  - Anomaly detection: precision@k, recall@k (evaluated against known anomalies if available)
- Stores metrics in `ai_model_performance` table

**ModelPromotionStep**
- Compares new model metrics against currently ACTIVE model
- Quality gates (configurable per model):
  - New model must be within X% of previous model's performance (prevents regression)
  - Minimum absolute thresholds (e.g., stockout predictor AUC must be > 0.75)
- If gates pass: new model promoted to ACTIVE, previous model RETIRED
- If gates fail: new model stays in EVALUATING status, alert generated for review

**DriftDetectionStep** (runs independently)
- Compares current feature distributions against training data distributions
- Uses Population Stability Index (PSI) or Kolmogorov-Smirnov test
- If drift detected: triggers early retraining and generates alert

### 7.3 Training Schedule

| Model | Frequency | Data Window | Typical Training Time |
|---|---|---|---|
| Demand Forecaster | Weekly | 6-12 months | 2-5 minutes |
| Stockout Predictor | Weekly | 6 months | 1-3 minutes |
| Shrinkage Detector | Weekly | 3 months | 1-2 minutes |
| Payment Anomaly Detector | Weekly | 6 months | 1-2 minutes |
| Data Quality Detector | Weekly | 3 months | 1-2 minutes |
| Payment Distress Classifier | Monthly | 12 months | 2-5 minutes |
| Rep Performance Predictor | Monthly | 6 months | 1-3 minutes |
| Credit Limit Predictor | Monthly | 12 months | 2-5 minutes |
| Credit Classifier | Monthly | 12+ months | 2-5 minutes |

All training runs scheduled during off-peak hours (2:00-5:00 AM EAT).

---

## 8. LLM Configuration

### 8.1 Provider Strategy

```
Primary:    Ollama (localhost) → Qwen 2.5 32B or Mixtral 8x7B
Fallback:   Cloud API → GPT-4 or Claude
```

LangChain4j model configuration supports multiple providers. Each AI Service specifies its preferred provider with automatic fallback.

### 8.2 LLM Usage by Module

| Module | Primary LLM | Fallback | Temperature | Max Tokens |
|---|---|---|---|---|
| Credit Scoring | Ollama (Qwen 2.5 32B) | GPT-4 / Claude | 0.1 | 1000 |
| Credit Explainer | Ollama (Qwen 2.5 32B) | GPT-4 / Claude | 0.2 | 500 |
| Order Suggestions | Ollama (Qwen 2.5 32B) | GPT-4 / Claude | 0.3 | 300 |
| Recommendation Agent | Cloud GPT-4 / Claude | Ollama (Qwen 2.5 32B) | 0.4 | 2000 |
| Compliance Reporting | Ollama (Qwen 2.5 32B) | GPT-4 / Claude | 0.3 | 3000 |

Note: Recommendation Agent defaults to cloud for higher reasoning quality. All others default to local.

### 8.3 RAG Configuration

- Embedding model: Ollama (nomic-embed-text or similar) for local embedding generation
- Embedding storage: pgvector extension in existing PostgreSQL
- Embedding dimensions: 768 (dependent on chosen embedding model)
- Similarity search: cosine similarity via pgvector `<=>` operator
- RAG contexts:
  - Credit scoring: similar merchant profiles (5 nearest neighbors)
  - Recommendations: past recommendations and outcomes
  - Compliance reporting: previously approved report sections

### 8.4 Resilience

- All LLM calls wrapped in Resilience4j circuit breakers
- Circuit breaker config: open after 5 failures in 60 seconds, half-open after 30 seconds
- Timeout: 30 seconds per LLM call (local), 60 seconds (cloud)
- Retry: 2 retries with exponential backoff
- Fallback chain: local LLM → cloud LLM → graceful degradation (return no AI result)

### 8.5 Fine-Tuning Path (Future)

Once sufficient data accumulates (6-12 months):
- Export training data from PostgreSQL (input-output pairs from approved credit evaluations, accepted reports)
- Fine-tune base model using external tools (Axolotl, Unsloth — Python, offline only)
- Deploy fine-tuned model to Ollama (same runtime, swapped model file)
- No architecture changes — just a model file swap

---

## 9. Event-Driven Integration

### 9.1 AI Event Flow

```
Existing Zuqi Workflow → Spring Event → AI Scoring Service → Result Storage / Alert

Examples:
  Payment recorded     → PaymentRecordedEvent  → PaymentAnomalyDetector → Alert if anomalous
  Stock adjusted       → StockAdjustedEvent     → ShrinkageDetector      → Alert if anomalous
  Order created        → OrderCreatedEvent       → DataQualityDetector    → Validation result
  Merchant onboarded   → MerchantCreatedEvent    → CreditScoringAiService → Credit evaluation
  Delivery completed   → DeliveryCompletedEvent  → MerchantFeatureService → Feature update
```

### 9.2 Implementation

**Initial (low volume):** Spring's `ApplicationEventPublisher` — in-process, synchronous or `@Async`

**At scale:** Migrate to RabbitMQ or Kafka for:
- Decoupling: AI processing doesn't slow down transactional workflows
- Buffering: spikes in order volume don't overwhelm ML inference
- Retry: failed AI scoring retried without affecting the source transaction

### 9.3 Event Schema

```java
public record PaymentRecordedEvent(
    UUID paymentId,
    UUID merchantId,
    UUID distributorId,
    BigDecimal amount,
    String paymentMethod,
    Instant occurredAt
) {}
```

Each event carries only IDs and minimal context. The AI service retrieves full data as needed — keeping events lightweight.

---

## 10. API Extensions

### 10.1 New REST Endpoints

Added under the existing API structure:

```
/v1/ai/credit
  POST   /evaluate/{merchantId}         → Trigger credit evaluation
  GET    /evaluations/{merchantId}       → Get evaluation history
  GET    /score/{merchantId}             → Get current credit score
  POST   /adjust/{merchantId}            → Trigger credit limit adjustment

/v1/ai/demand
  GET    /forecast/{merchantId}          → Get demand forecast for merchant
  GET    /forecast/warehouse/{warehouseId} → Get aggregated warehouse forecast
  GET    /suggestions/{merchantId}       → Get order suggestions for sales rep

/v1/ai/anomaly
  GET    /alerts                         → List anomaly alerts (filterable)
  PUT    /alerts/{alertId}/acknowledge   → Acknowledge alert
  PUT    /alerts/{alertId}/resolve       → Resolve alert
  GET    /alerts/summary                 → Alert summary for dashboard

/v1/ai/prediction
  GET    /stockout/{warehouseId}         → Get stockout predictions
  GET    /rep-performance                → Get rep performance predictions

/v1/ai/routing
  POST   /optimize                       → Trigger route optimization
  GET    /routes/{date}                  → Get routes for a date
  POST   /reoptimize                     → Trigger intraday re-optimization
  GET    /routes/{routeId}               → Get specific route detail

/v1/ai/recommendations
  GET    /{distributorId}                → Get recommendations
  PUT    /{recommendationId}/accept      → Mark recommendation accepted
  PUT    /{recommendationId}/reject      → Mark recommendation rejected

/v1/ai/reports
  POST   /compliance/generate            → Generate compliance report
  GET    /compliance/{reportId}          → Get generated report

/v1/ai/system
  GET    /health                         → AI system health check
  GET    /models                         → List active models and versions
  GET    /models/{modelName}/performance → Model performance metrics
```

### 10.2 Authorization

AI endpoints follow existing Casbin RBAC patterns:

| Endpoint Group | Accessible By |
|---|---|
| /v1/ai/credit | DISTRIBUTOR_ADMIN, FINANCE |
| /v1/ai/demand | DISTRIBUTOR_ADMIN, SALES_REP, WAREHOUSE_MANAGER |
| /v1/ai/anomaly | DISTRIBUTOR_ADMIN, WAREHOUSE_MANAGER, FINANCE |
| /v1/ai/prediction | DISTRIBUTOR_ADMIN, WAREHOUSE_MANAGER |
| /v1/ai/routing | DISTRIBUTOR_ADMIN, DRIVER |
| /v1/ai/recommendations | DISTRIBUTOR_ADMIN |
| /v1/ai/reports | DISTRIBUTOR_ADMIN |
| /v1/ai/system | SUPER_ADMIN, ADMIN |

---

## 11. Monitoring and Observability

### 11.1 Model Performance Monitoring

**Prediction distribution tracking:**
- For each model, track the distribution of predictions over time
- Alert if distribution shifts significantly (e.g., credit model suddenly assigning B to 80% of merchants)

**Accuracy tracking (business outcome feedback):**
- Credit scoring: actual default rate per grade bracket
- Demand forecasting: predicted vs. actual order quantities (MAPE)
- Stockout prediction: predicted stockouts vs. actual stockouts
- Anomaly detection: flagged anomalies that were confirmed vs. dismissed

**Data drift detection:**
- Compare current feature distributions against training data
- PSI (Population Stability Index) computed weekly per model
- Threshold: PSI > 0.2 triggers retraining alert

### 11.2 System Health Metrics (Prometheus)

```
# LLM metrics
zuqi_ai_llm_requests_total{provider, model, module}
zuqi_ai_llm_latency_seconds{provider, model, module}
zuqi_ai_llm_errors_total{provider, model, error_type}
zuqi_ai_llm_tokens_used{provider, model, direction}   # input/output

# ML model metrics
zuqi_ai_model_inference_total{model_name, model_version}
zuqi_ai_model_inference_latency_ms{model_name}
zuqi_ai_model_training_duration_seconds{model_name}

# Pipeline metrics
zuqi_ai_training_runs_total{model_name, status}        # success/failure
zuqi_ai_feature_computation_duration_seconds{feature_service}
zuqi_ai_forecast_records_generated{date}

# Route optimization metrics
zuqi_ai_route_solver_duration_seconds
zuqi_ai_route_stops_total{date}
zuqi_ai_route_distance_planned_vs_actual_km

# Alert metrics
zuqi_ai_alerts_generated_total{alert_type, severity}
zuqi_ai_alerts_resolution_time_hours{alert_type}
```

### 11.3 Grafana Dashboards

**AI Operations Dashboard:**
- LLM request rate and latency
- ML inference rate and latency
- Model training pipeline status
- Active model versions

**Model Quality Dashboard:**
- Prediction accuracy trends per model
- Data drift indicators
- Feature distribution changes
- Business outcome tracking (default rates, forecast accuracy)

**Alert Dashboard:**
- Open alerts by type and severity
- Alert resolution rate
- False positive rate (dismissed alerts / total alerts)

---

## 12. Infrastructure Deployment

### 12.1 Deployment Architecture

```
┌──────────────────────────────────────────────────────┐
│                   Docker Compose / K8s                │
│                                                      │
│  ┌─────────────────┐    ┌──────────────────────────┐ │
│  │  Zuqi Spring     │    │  Ollama                  │ │
│  │  Boot App        │───▶│  (Qwen 2.5 32B)         │ │
│  │  (AI modules     │    │  GPU: NVIDIA A10G/L4     │ │
│  │   included)      │    └──────────────────────────┘ │
│  │                  │                                 │
│  │                  │───▶ PostgreSQL + pgvector        │
│  │                  │───▶ Redis                        │
│  │                  │───▶ GraphHopper (embedded or     │
│  │                  │     sidecar with Kenya OSM data) │
│  └─────────────────┘                                  │
│                                                       │
│  ┌─────────────────┐                                  │
│  │  Prometheus +    │                                  │
│  │  Grafana         │                                  │
│  └─────────────────┘                                  │
└───────────────────────────────────────────────────────┘
          │
          │ (fallback only)
          ▼
    Cloud LLM API
    (GPT-4 / Claude)
```

### 12.2 Resource Estimates

| Component | CPU | RAM | GPU | Storage |
|---|---|---|---|---|
| Zuqi Spring Boot (with AI) | 4 vCPU | 16 GB | — | — |
| Ollama (Qwen 2.5 32B) | 4 vCPU | 16 GB | 24 GB VRAM (A10G/L4) | 20 GB (model files) |
| PostgreSQL + pgvector | 4 vCPU | 16 GB | — | 100 GB+ |
| Redis | 2 vCPU | 8 GB | — | — |
| GraphHopper | 2 vCPU | 4 GB | — | 2 GB (Kenya OSM) |
| Prometheus + Grafana | 2 vCPU | 4 GB | — | 50 GB |

### 12.3 Estimated Monthly Infrastructure Cost

| Component | Estimated Cost (Cloud) |
|---|---|
| GPU instance (A10G) | $400-500/month |
| Application server | $150-200/month |
| PostgreSQL (managed) | $100-200/month |
| Redis (managed) | $50-100/month |
| Monitoring | $50-100/month |
| Cloud LLM API (fallback/agent) | $100-300/month |
| **Total** | **$850-1,400/month** |

---

## 13. Security and Compliance

### 13.1 Data Protection

- All merchant financial data processed locally (Ollama) — never sent to external LLM providers except for fallback/agent use
- Cloud LLM calls: merchant data anonymized where possible (use category codes instead of names, aggregate metrics instead of raw transactions)
- pgvector embeddings: contain derived features only, not raw PII
- Model registry: model binaries encrypted at rest
- Prediction audit log: retained for minimum 7 years (KCB compliance requirement)

### 13.2 Kenya Data Protection Act Compliance

- Data processing occurs within controlled infrastructure
- No cross-border data transfer for local LLM inference
- Cloud LLM fallback: data processing agreement required with provider
- Merchant consent: credit scoring requires explicit consent (tracked in merchant profile)
- Right to explanation: `CreditExplainer` provides human-readable reasoning for any AI decision affecting a merchant

### 13.3 AI-Specific Security

- LLM prompt injection protection: input sanitization before LLM calls
- Agent tool calls: bounded (max 10 per run), each tool validates authorization
- Model poisoning prevention: training data validated before model training
- Adversarial input detection: extreme feature values flagged before model inference

---

*This blueprint defines the complete AI system architecture for Zuqi. All modules are designed to integrate into the existing Spring Boot application with minimal changes to existing code. The architecture supports evolution from LLM-based solutions to trained ML models as operational data accumulates.*