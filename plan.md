# Zuqi AI Integration — System Architecture Blueprint

**Version:** 2.1
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
- **Day-one ready.** Synthetic data generated in-memory bootstraps all ML models at launch. Models improve progressively as real data replaces synthetic data through a managed SYNTHETIC → HYBRID → REAL transition.
- **Auditable.** Every AI decision is logged with inputs, outputs, model version, confidence score, and data composition (synthetic vs real ratio). Required for the KCB banking partnership.
- **Evolvable.** Architecture supports migrating from LLM-based solutions to trained ML models as data accumulates, and from synthetic-trained models to real-data-trained models as operational history grows.

### 1.3 AI Capability Summary

| # | Capability | AI Type | Primary Technology | Day-One Ready |
|---|---|---|---|---|
| 1 | Credit Risk Scoring | LLM Chain → ML Classifier | LangChain4j → Tribuo | ✅ LLM + synthetic ML |
| 2 | AI-Powered Order Suggestions | ML Regression + optional LLM | Tribuo + LangChain4j | ✅ Synthetic-trained |
| 3 | Demand Forecasting | ML Regression | Tribuo (XGBoost) | ✅ Synthetic-trained |
| 4 | Dynamic Route Optimization | Constraint Solver | Timefold + GraphHopper | ✅ No training needed |
| 5 | Inventory Shrinkage Detection | ML Anomaly Detection | Tribuo (Isolation Forest) | ✅ Synthetic patterns |
| 6 | Payment Anomaly Detection | ML Anomaly + Classification | Tribuo | ✅ Synthetic patterns |
| 7 | Stockout Prediction | ML Classification | Tribuo (XGBoost) | ✅ Synthetic-trained |
| 8 | Sales Rep Underperformance | ML Regression | Tribuo (XGBoost) | ✅ Synthetic baselines |
| 9 | Dynamic Credit Limit Adjustment | ML Regression + Rules | Tribuo (XGBoost) | ✅ Synthetic-trained |
| 10 | Operational Recommendations | AI Agent | LangChain4j Agent | ✅ LLM-based |
| 11 | Compliance Reporting | LLM Chain | LangChain4j AI Service | ✅ LLM-based |
| 12 | Data Quality Detection | Rules + ML | Java Rules + Tribuo | ✅ Rules + synthetic ML |

---

## 2. Technology Stack

### 2.1 Core AI Technologies

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| LLM Framework | LangChain4j | 0.35.0 | LLM chains, agents, RAG, structured output |
| Local LLM Runtime | Ollama | Running at http://192.168.2.17:11434 | Hosts local open-source LLMs |
| Local LLM Model | qwen2.5-coder:32b | Active | Credit scoring, suggestions, reporting |
| Embedding Model | nomic-embed-text | Active | RAG embeddings (768 dimensions) |
| Cloud LLM (fallback) | **DISABLED** | N/A | **Local-only deployment - no cloud fallback** |
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
| Synthetic Data | In-memory generators + DataMixer | Day-one model bootstrap, managed transition to real data |
| Monitoring | Prometheus + Grafana | Model performance, drift detection, data maturity tracking |
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
        ├── synthetic/                   (NEW — in-memory generation & transition)
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
│   │   ├── LangChain4jConfig      # LLM provider configuration (local Ollama only)
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
│   │   ├── FeatureStore             # Centralized feature access and caching
│   │   └── FeatureComputer          # Shared computation logic (stateless, data-source agnostic)
│   │
│   ├── model/                      # ML model management
│   │   ├── ModelRegistry            # Model versioning and metadata
│   │   ├── ModelTrainer             # Generic training pipeline orchestrator
│   │   ├── ModelEvaluator           # Model performance evaluation
│   │   └── ModelLoader              # Loads active models for inference
│   │
│   ├── credit/                     # Credit risk AI module
│   │   ├── CreditScoringAiService   # LangChain4j AI Service interface
│   │   ├── CreditClassifier         # Tribuo XGBoost classifier (synthetic → real)
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
│   │   ├── PaymentDistressClassifier # Tribuo XGBoost for distress (synthetic → real)
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
│   │       ├── Vehicle
│   │       ├── DeliveryStop
│   │       └── RoutePlan
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
│   ├── synthetic/                  # In-memory synthetic data generation & transition
│   │   ├── SyntheticDataConfig      # Volumes, archetype ratios, seed values
│   │   ├── SyntheticDatasetBuilder  # Builds complete in-memory synthetic datasets
│   │   ├── DataPhaseTracker         # Tracks SYNTHETIC/HYBRID/REAL per model
│   │   ├── DataMixer                # Blends synthetic + real Tribuo datasets
│   │   ├── TransitionEvaluator      # Evaluates readiness to phase out synthetic
│   │   ├── generators/              # Domain-specific in-memory generators
│   │   │   ├── MerchantProfileGenerator    # → List<SyntheticMerchant>
│   │   │   ├── OrderHistoryGenerator       # → List<SyntheticOrder>
│   │   │   ├── PaymentBehaviorGenerator    # → List<SyntheticPayment>
│   │   │   ├── InventoryMovementGenerator  # → List<SyntheticMovement>
│   │   │   ├── SalesRepActivityGenerator   # → List<SyntheticRepActivity>
│   │   │   └── CreditHistoryGenerator      # → List<SyntheticCreditEvent>
│   │   └── profiles/                # Behavioral archetypes
│   │       ├── MerchantArchetypes
│   │       ├── SeasonalityPatterns
│   │       └── AnomalyPatterns
│   │
│   ├── pipeline/                   # Training pipeline orchestration
│   │   ├── TrainingPipelineJob      # Spring Batch master training job
│   │   ├── FeatureComputationStep   # Batch step: compute features from real data
│   │   ├── SyntheticDatasetStep     # Batch step: generate synthetic dataset in-memory
│   │   ├── DataMixingStep           # Batch step: blend synthetic + real datasets
│   │   ├── ModelTrainingStep        # Batch step: train models
│   │   ├── ModelEvaluationStep      # Batch step: evaluate on test set
│   │   ├── ModelPromotionStep       # Batch step: promote if metrics pass
│   │   ├── PhaseEvaluationStep      # Batch step: evaluate data phase transition
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
    ├── synthetic/     ← feature/ (FeatureComputer provides shared computation logic)
    ├── credit/        ← feature/, model/, synthetic/
    ├── demand/        ← feature/, model/, synthetic/
    ├── anomaly/       ← feature/, model/, synthetic/
    ├── prediction/    ← feature/, model/, demand/, synthetic/
    ├── routing/       ← (independent — uses order data directly, no synthetic needed)
    ├── agent/         ← credit/, demand/, anomaly/, prediction/ (reads from all)
    ├── reporting/     ← (independent — uses operational data directly)
    │
    Model Registry (model/) ← all ML modules register and load models through this
    │
    DataMixer (synthetic/) ← all training pipelines blend datasets through this
    │
    DataPhaseTracker (synthetic/) ← all training pipelines evaluate transitions through this
    │
    Training Pipeline (pipeline/) ← orchestrates feature/, model/, synthetic/, and all ML modules
    │
    Monitoring (monitoring/) ← observes all modules including data maturity
```

---

## 4. Feature Engineering Layer

### 4.1 Purpose

The feature engineering layer is the foundation of the entire AI system. It computes derived features from raw operational data that all ML models and LLM prompts consume. Centralizing feature logic ensures consistency between training and inference — the single most important requirement for reliable ML.

### 4.2 Shared Computation via FeatureComputer

Feature computation logic is extracted into a stateless `FeatureComputer` utility class. This class accepts raw data (as lists of records/DTOs) and returns computed features. Both real feature services and synthetic generators call the same `FeatureComputer` methods — **only the data source differs, not the computation.**

```java
// Stateless — accepts raw data, returns computed features
public class FeatureComputer {

    // Called by MerchantFeatureService (real data from JPA)
    // AND by SyntheticDatasetBuilder (in-memory synthetic data)
    public MerchantFeatures computeMerchantFeatures(
        List<OrderRecord> orders,
        List<PaymentRecord> payments,
        MerchantProfile merchant,
        CreditInfo credit) { ... }

    public DemandFeatures computeDemandFeatures(
        List<OrderItemRecord> orderHistory,
        MerchantProfile merchant,
        ProductInfo product,
        LocalDate asOfDate) { ... }

    // ... same pattern for all feature types
}
```

This guarantees zero drift between real and synthetic feature pipelines.

### 4.3 Feature Services (Real Data)

#### MerchantFeatureService
Queries JPA repositories → passes data to `FeatureComputer.computeMerchantFeatures()`.

**Order features:** total_orders, order_frequency_per_week, avg_order_value, order_value_trend_slope_12w, order_consistency_stddev, cancellation_rate, return_rate, days_since_last_order, unique_skus_ordered, top_sku_concentration

**Payment features:** total_payments, on_time_payment_pct, avg_days_to_pay, worst_days_to_pay, partial_payment_frequency, payment_method_distribution (JSON), consecutive_on_time_streak, total_overdue_amount

**Credit features:** current_credit_limit, current_utilization_ratio, peak_utilization_ratio, utilization_trend_slope, limit_increase_count, days_since_last_limit_change

**Profile features:** business_category (encoded), relationship_tenure_days, verification_status, geographic_cluster

#### OrderFeatureService (Demand Features)
Queries JPA repositories → passes data to `FeatureComputer.computeDemandFeatures()`.

**Lag features:** qty_1w_ago, qty_2w_ago, qty_3w_ago, qty_4w_ago, rolling_avg_4w, rolling_avg_12w, trend_direction

**Temporal features:** day_of_week, week_of_month, month_of_year, is_holiday, is_payday_week, is_ramadan, is_christmas_season

**Merchant context:** merchant_category, merchant_size_tier, merchant_credit_status, merchant_tenure

**SKU context:** product_category, price_tier, is_promotional, typical_shelf_life

#### PaymentFeatureService
**Per-payment features:** days_to_payment_vs_merchant_avg, amount_vs_invoice_amount_ratio, payment_method_encoded, hour_of_day, is_partial, gap_since_last_payment_days

**Merchant trend features:** days_to_pay_trend_3m, order_frequency_trend_3m, credit_utilization_trajectory, partial_payment_freq_trend, avg_order_value_trend

#### InventoryFeatureService
**Per warehouse-SKU:** current_stock, expected_stock, discrepancy, discrepancy_pct, manual_adjustment_count_7d, adjustment_time_distribution, adjusting_user_ids, consumption_rate_7d, consumption_rate_30d, consumption_trend, pending_reserved_qty, expected_incoming_qty

#### SalesRepFeatureService
**Per-rep per-period:** visit_count_vs_target, order_conversion_rate, total_order_value, avg_order_value, new_merchants_acquired, collection_rate, route_adherence_pct, territory_penetration_pct

### 4.4 FeatureStore

Centralized access for real-time inference and real-data training:

- **Computes or retrieves** cached features via Redis
- **TTL-based caching** — merchant features 24h, demand nightly, payment per event
- **Training mode** — computes historical features for a date range
- **Inference mode** — computes current features for a single entity
- **Does NOT handle synthetic data** — synthetic features are computed in-memory by `SyntheticDatasetBuilder` using `FeatureComputer` directly

---

## 5. ML Model Management

### 5.1 Model Registry (Database Schema)

```sql
-- Model metadata and versioning
CREATE TABLE ai_model_registry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name      VARCHAR(100) NOT NULL,
    model_version   INTEGER NOT NULL,
    algorithm       VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,       -- TRAINING, EVALUATING, ACTIVE, RETIRED
    distributor_id  UUID REFERENCES distributors(id) ON DELETE CASCADE,
    data_phase      VARCHAR(20),                -- SYNTHETIC, HYBRID, REAL
    real_data_ratio DOUBLE PRECISION,           -- 0.0 to 1.0
    synthetic_records_used INTEGER,
    real_records_used INTEGER,
    training_data_start TIMESTAMP,
    training_data_end   TIMESTAMP,
    training_record_count INTEGER,
    performance_metrics JSONB,
    hyperparameters     JSONB,
    model_binary        BYTEA,
    model_size_bytes    BIGINT,
    feature_columns     JSONB,
    created_at      TIMESTAMP DEFAULT NOW(),
    created_by      VARCHAR(100),
    promoted_at     TIMESTAMP,
    retired_at      TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(model_name, model_version)
);

CREATE INDEX idx_model_active ON ai_model_registry(model_name, status)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_model_registry_created ON ai_model_registry(created_at DESC);
CREATE INDEX idx_model_registry_distributor ON ai_model_registry(distributor_id) WHERE distributor_id IS NOT NULL;

-- Individual prediction audit log
CREATE TABLE ai_predictions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name      VARCHAR(100) NOT NULL,
    model_version   INTEGER NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    distributor_id  UUID REFERENCES distributors(id) ON DELETE CASCADE,
    input_features_hash VARCHAR(64),
    prediction_value    JSONB NOT NULL,
    confidence_score    DOUBLE PRECISION,       -- Phase-adjusted confidence
    raw_confidence      DOUBLE PRECISION,       -- Pre-adjustment confidence
    data_phase          VARCHAR(20),            -- Phase at time of prediction
    was_overridden      BOOLEAN DEFAULT FALSE,
    override_value      JSONB,
    override_by         VARCHAR(100),
    override_reason     TEXT,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW()
);

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
    metric_name     VARCHAR(50) NOT NULL,
    metric_value    DOUBLE PRECISION NOT NULL,
    sample_size     INTEGER,
    data_phase      VARCHAR(20),
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(model_name, model_version, evaluation_date, metric_name)
);

CREATE INDEX idx_model_performance_lookup ON ai_model_performance(model_name, model_version, evaluation_date);

-- Demand forecasts
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
    data_phase      VARCHAR(20),
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(merchant_id, sku_id, forecast_date)
);

CREATE INDEX idx_demand_forecasts_merchant ON ai_demand_forecasts(merchant_id);
CREATE INDEX idx_demand_forecasts_date ON ai_demand_forecasts(forecast_date);
CREATE INDEX idx_demand_forecasts_distributor ON ai_demand_forecasts(distributor_id);

-- Anomaly alerts
CREATE TABLE ai_anomaly_alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_type      VARCHAR(50) NOT NULL,
    severity        VARCHAR(20) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    anomaly_score   DOUBLE PRECISION,
    description     TEXT NOT NULL,
    context         JSONB,
    data_phase      VARCHAR(20),
    status          VARCHAR(20) DEFAULT 'OPEN',
    resolved_by     VARCHAR(100),
    resolved_at     TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

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
    priority        VARCHAR(20) NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    acted_on_at     TIMESTAMP,
    outcome         TEXT,
    model_version   VARCHAR(50),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_recommendations_distributor ON ai_recommendations(distributor_id);
CREATE INDEX idx_recommendations_status ON ai_recommendations(status);
CREATE INDEX idx_recommendations_created ON ai_recommendations(created_at DESC);

-- Delivery routes
CREATE TABLE ai_delivery_routes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    route_date      DATE NOT NULL,
    vehicle_id      UUID,
    vehicle_info    JSONB,
    driver_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stop_sequence   JSONB NOT NULL,
    total_distance_km   DOUBLE PRECISION,
    total_duration_min  DOUBLE PRECISION,
    load_utilization_pct DOUBLE PRECISION,
    solver_time_ms      INTEGER,
    status          VARCHAR(20) DEFAULT 'PLANNED',
    actual_distance_km  DOUBLE PRECISION,
    actual_duration_min DOUBLE PRECISION,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_delivery_routes_distributor ON ai_delivery_routes(distributor_id);
CREATE INDEX idx_delivery_routes_date ON ai_delivery_routes(route_date);
CREATE INDEX idx_delivery_routes_driver ON ai_delivery_routes(driver_id);
CREATE INDEX idx_delivery_routes_status ON ai_delivery_routes(status);

-- Merchant embeddings for RAG
CREATE TABLE ai_merchant_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    embedding       vector(768),
    feature_summary TEXT,
    model_version   VARCHAR(50),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(merchant_id)
);

CREATE INDEX idx_merchant_embeddings_similarity ON ai_merchant_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Recommendation embeddings for RAG
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

CREATE INDEX idx_recommendation_embeddings_similarity ON ai_recommendation_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Data phase tracking (lightweight — only metadata, no raw data)
CREATE TABLE ai_data_phase (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name      VARCHAR(100) NOT NULL,
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    current_phase   VARCHAR(20) NOT NULL DEFAULT 'SYNTHETIC',
    real_data_count BIGINT DEFAULT 0,
    real_data_ratio DOUBLE PRECISION DEFAULT 0.0,
    phase_changed_at TIMESTAMP,
    last_evaluated_at TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(model_name, distributor_id)
);

CREATE INDEX idx_data_phase_lookup ON ai_data_phase(model_name, distributor_id);

-- Synthetic generation run audit log
CREATE TABLE ai_synthetic_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    distributor_id  UUID NOT NULL REFERENCES distributors(id) ON DELETE CASCADE,
    run_type        VARCHAR(50) NOT NULL,       -- INITIAL_SEED, RETRAINING
    seed_value      BIGINT,                     -- For reproducibility
    archetype_config JSONB,                     -- Archetype ratios used
    records_generated JSONB,                    -- {orders: 150000, payments: 160000, ...}
    duration_ms     BIGINT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_synthetic_runs_distributor ON ai_synthetic_runs(distributor_id);
```

### 5.2 Model Lifecycle

```
TRAINING → EVALUATING → ACTIVE → RETIRED

1. TRAINING:   Model is being trained on latest data (synthetic, hybrid, or real)
2. EVALUATING: Model evaluated against held-out test set
3. ACTIVE:     Passed quality gates, serving predictions (one ACTIVE per model_name)
4. RETIRED:    Replaced by newer version, kept for audit

Each transition records: data_phase, real_data_ratio, synthetic_records_used, real_records_used
```

### 5.3 ModelLoader

- On startup, loads all ACTIVE models into `ConcurrentHashMap<String, Model<?>>`
- Hot-swaps on new promotion without restart
- Graceful degradation: returns null if no model exists

---

## 6. Synthetic Data Layer

### 6.1 Purpose

The synthetic data layer solves the cold-start problem: every ML model requires months of historical data to train, but at launch (and when each new distributor onboards) that data doesn't exist. Synthetic data generated **in-memory** provides statistically realistic training data so all 9 ML models are operational from day one.

### 6.2 In-Memory Architecture

Synthetic data is **never persisted to the database.** The flow:

```
Generators → In-memory DTOs → FeatureComputer → Tribuo Dataset<T> → DataMixer → Trainer
                                                                         ↑
                                                        Real features from FeatureStore
```

**What IS persisted:**
- `ai_data_phase` — tracks current phase per model per distributor (small metadata table)
- `ai_synthetic_runs` — logs generation parameters and seed values (audit/reproducibility)
- Model registry columns — `data_phase`, `real_data_ratio`, `synthetic_records_used`, `real_records_used`

**What is NOT persisted:**
- Raw synthetic orders, payments, inventory movements, credit events, rep activities
- These are generated fresh each training run, features computed, then discarded

**Why in-memory:**
- Zero database bloat (no hundreds of thousands of synthetic records per distributor)
- No mirror table maintenance (schema changes to real tables don't require matching synthetic tables)
- No risk of synthetic data leaking into operational queries
- Reproducible via seed values logged in `ai_synthetic_runs`
- Training runs take minutes, not hours — in-memory is fast enough

### 6.3 Three-Phase Data Lifecycle

```
Phase 1: SYNTHETIC-ONLY (Day 0 — launch)
  └─ Generators produce in-memory data → FeatureComputer → 100% synthetic dataset
  └─ Predictions carry 0.6x confidence modifier
  └─ All 9 ML models operational immediately

Phase 2: HYBRID (Real data accumulating)
  └─ Real features from FeatureStore + synthetic features from generators
  └─ DataMixer blends: real weight 1.0, synthetic weight declines
  └─ Confidence: 0.6 + (0.4 × real_data_ratio)

Phase 3: REAL-ONLY (Sufficient real data)
  └─ Generators no longer called — training uses only real data
  └─ Confidence modifier: 1.0
  └─ Synthetic generation disabled for that model
```

### 6.4 Transition Thresholds

| Model | Synthetic → Hybrid | Hybrid → Real-Only |
|---|---|---|
| Demand Forecaster | 50 real orders/merchant-SKU | 6 months + 200 orders/merchant-SKU |
| Stockout Predictor | 100 real inventory snapshots | 6 months + 20 stockout events |
| Shrinkage Detector | 500 real stock movements | 3 months + 10 confirmed shrinkage events |
| Payment Anomaly Detector | 200 real payments | 6 months + 500 payments |
| Payment Distress Classifier | 500 real payments | 12 months + 100 default events |
| Rep Performance Predictor | 8 weeks of real rep data | 6 months of real rep data |
| Credit Classifier | 200 credit evaluations | 12 months + 100 default events |
| Credit Limit Regressor | 100 credit limit changes | 12 months + 200 limit adjustments |
| Data Quality Detector (ML) | 1,000 real data entries | 3 months + 5,000 entries |

### 6.5 Merchant Archetypes

| Archetype | Population % | Order Freq (wk) | Avg Order (KES) | On-Time Rate | Growth | Default Prob |
|---|---|---|---|---|---|---|
| STEADY_GROWER | 35% | 3.5 ± 0.8 | 15,000 ± 5,000 | 92% ± 5% | +3%/mo | 2% |
| STABLE_PERFORMER | 25% | 2.5 ± 0.5 | 12,000 ± 3,000 | 88% ± 6% | 0%/mo | 5% |
| INCONSISTENT_BUYER | 20% | 1.5 ± 1.0 | 8,000 ± 4,000 | 72% ± 12% | -1%/mo | 15% |
| NEW_ENTRANT | 10% | 1.0 ± 0.5 | 5,000 ± 2,000 | 85% ± 8% | +8%/mo | 10% |
| DECLINING_RISK | 7% | 2.0 ± 0.8 | 10,000 ± 4,000 | 60% ± 15% | -5%/mo | 40% |
| DEFAULTER | 3% | 1.5 ± 1.0 | 7,000 ± 3,000 | 45% ± 20% | -10%/mo | 100% |

### 6.6 Generators (In-Memory)

Each generator produces plain Java DTOs (records), not JPA entities:

```java
// Generators produce lightweight records — no database interaction
public record SyntheticMerchant(String id, MerchantArchetype archetype,
    String category, String county, double lat, double lng, LocalDate registered) {}

public record SyntheticOrder(String merchantId, LocalDate date,
    List<SyntheticOrderItem> items, BigDecimal totalAmount) {}

public record SyntheticPayment(String orderId, String merchantId,
    LocalDate paymentDate, int daysToPayment, BigDecimal amount,
    boolean isPartial, String method) {}
// ... etc
```

**MerchantProfileGenerator** — 1,000 merchants, archetype-distributed, county-weighted, LLM-generated names

**OrderHistoryGenerator** — 12 months per merchant, seasonality applied (Dec-Jan +30%, Mar-Apr -10%, Ramadan +20% food, payday +25%)

**PaymentBehaviorGenerator** — linked to orders, DEFAULTER sequence: normal 3-6mo → deterioration → 90+ day default

**InventoryMovementGenerator** — shrinkage injection at 5%: concentrated time/user patterns

**SalesRepActivityGenerator** — underperformance injection at 10% of reps

**CreditHistoryGenerator** — DEFAULTER progression: A→B→C→D→F over 6-12 months

### 6.7 SyntheticDatasetBuilder

Central coordinator that generates a complete synthetic Tribuo `Dataset<T>` for any model:

```java
public class SyntheticDatasetBuilder {

    /**
     * Generates synthetic data in-memory, computes features using
     * FeatureComputer, returns a Tribuo Dataset ready for training.
     * No database reads or writes.
     */
    public Dataset<Regressor> buildDemandDataset(UUID distributorId, SyntheticDataConfig config) {
        // Step 1: Generate synthetic merchants
        List<SyntheticMerchant> merchants = merchantGenerator.generate(config);
        // Step 2: Generate order history per merchant
        List<SyntheticOrder> orders = orderGenerator.generate(merchants, config);
        // Step 3: Compute features using shared FeatureComputer
        List<Example<Regressor>> examples = orders.stream()
            .map(order -> featureComputer.computeDemandFeatures(
                orderHistory.getFor(order.merchantId(), order.skuId()),
                merchantLookup.get(order.merchantId()),
                productLookup.get(order.skuId()),
                order.date()))
            .map(features -> toTribuoExample(features, actualQuantity))
            .toList();
        // Step 4: Return dataset — nothing persisted
        return new MutableDataset<>(new ListDataSource<>(examples));
    }

    // Similar methods: buildCreditDataset(), buildShrinkageDataset(), etc.
}
```

### 6.8 DataMixer

Blends synthetic and real Tribuo datasets based on current phase:

```
SYNTHETIC phase:  return syntheticDataset (generators called)
HYBRID phase:     blend realDataset + syntheticDataset (weighted)
REAL phase:       return realDataset (generators NOT called — skipped entirely)
```

Weighting: real = 1.0, synthetic = max(0.2, 1.0 - real_ratio). Floor of 0.2 preserves rare events.

**Anomaly class preservation:** Minimum rare event representation maintained:
- Credit classifier: ≥ 5% default class
- Shrinkage detector: ≥ 10% anomalous
- Payment anomaly: ≥ 8% anomalous
- Stockout predictor: ≥ 10% stockout-positive

### 6.9 Confidence Modifier

```
SYNTHETIC:  confidence = raw_confidence × 0.6
HYBRID:     confidence = raw_confidence × (0.6 + 0.4 × real_data_ratio)
REAL:       confidence = raw_confidence × 1.0
```

Synthetic-phase credit decisions always route to human review (confidence never reaches auto-approval threshold).

### 6.10 Training Flow with Synthetic Data

```
Every training pipeline:
  1. FeatureComputationStep    → compute real features from FeatureStore
  2. SyntheticDatasetStep      → IF phase != REAL: generate synthetic dataset in-memory
  3. DataMixingStep            → blend based on phase (or passthrough if REAL)
  4. DataSplitStep             → 80/20 train/test
  5. ModelTrainingStep         → train Tribuo model
  6. ModelEvaluationStep       → evaluate, record metrics with data_phase tag
  7. ModelPromotionStep        → promote if gates pass, record data composition
  8. PhaseEvaluationStep       → check transition thresholds, fire event if phase changes
```

In REAL phase, steps 2 and 3 are skipped — no synthetic data generated, no mixing overhead.

---

## 7. Detailed Module Specifications

### 7.1 Credit Risk Module (`ai/credit/`)

**CreditScoringAiService** (LangChain4j AI Service)
- Local-only Ollama at http://192.168.2.17:11434, qwen2.5-coder:32b
- Temperature: 0.1, timeout: 30s, Resilience4j circuit breaker

**CreditClassifier** (Tribuo XGBoost)
- Trains on synthetic default data from day one via DataMixer
- Phase-aware hybrid scoring:
  - SYNTHETIC: LLM primary, ML shadow (log comparison only)
  - HYBRID: both run, agreement → ML, disagreement → human review
  - REAL: ML primary, LLM retired

**CreditFeatureBuilder** — same data, two formats: LLM profile + Tribuo Example

**CreditExplainer** — human-readable ML decision explanations

**CreditLimitAdjuster** — XGBoost regression, synthetic-trained, synthetic-phase → human review

### 7.2 Demand Forecasting Module (`ai/demand/`)

**DemandForecaster** — XGBoost regression, trains via DataMixer (synthetic from day one)

**DemandFeatureBuilder** — builds real datasets from FeatureStore; `SyntheticDatasetBuilder` handles synthetic

**DemandForecastJob** — nightly batch, confidence modifier applied

**OrderSuggestionService** — available from day one, ranks by phase-adjusted confidence

### 7.3 Anomaly Detection Module (`ai/anomaly/`)

**ShrinkageDetector** — Isolation Forest, trains on synthetic shrinkage patterns
**PaymentAnomalyDetector** — Isolation Forest, trains on synthetic payment patterns
**PaymentDistressClassifier** — XGBoost, trains on synthetic deterioration sequences from day one
**DataQualityDetector** — Tier 1 rules (immediate), Tier 2 ML (synthetic-trained)
**AlertService** — alerts include `data_phase` context

### 7.4 Predictive Alerts Module (`ai/prediction/`)

**StockoutPredictor** — XGBoost, synthetic-bootstrapped, nightly after demand forecasts
**RepPerformancePredictor** — XGBoost, synthetic baselines, weekly

### 7.5 Route Optimization Module (`ai/routing/`)

No synthetic data needed. Timefold solver + GraphHopper for Kenya roads.

### 7.6 AI Agent Module (`ai/agent/`)

No synthetic data needed. LLM-based, 7 tools, weekly recommendations.

### 7.7 Compliance Reporting Module (`ai/reporting/`)

No synthetic data needed. LLM-based, principal-specific templates, monthly.

---

## 8. Training Pipeline Architecture

### 8.1 Pipeline Overview

```
FeatureComputationStep → SyntheticDatasetStep → DataMixingStep → DataSplitStep →
ModelTrainingStep → ModelEvaluationStep → ModelPromotionStep → PhaseEvaluationStep
```

Steps 2-3 are skipped when model is in REAL phase.

### 8.2 Pipeline Steps

**FeatureComputationStep** — computes real features via FeatureStore

**SyntheticDatasetStep** — calls `SyntheticDatasetBuilder` to generate in-memory dataset; skipped if REAL phase

**DataMixingStep** — `DataMixer` blends based on phase; passthrough if REAL

**DataSplitStep** — time-based 80/20 train/test

**ModelTrainingStep** — trains Tribuo model on blended dataset

**ModelEvaluationStep** — evaluates, records metrics with `data_phase` tag

**ModelPromotionStep** — quality gates (relaxed during SYNTHETIC, tightened as real ratio increases); records full data composition in registry

**PhaseEvaluationStep** — `DataPhaseTracker.evaluatePhase()`; publishes `DataPhaseTransitionEvent` if threshold met

**DriftDetectionStep** — PSI weekly, threshold 0.2 triggers retraining

### 8.3 Training Schedule

| Model | Frequency | Typical Training Time |
|---|---|---|
| Demand Forecaster | Weekly | 2-5 minutes |
| Stockout Predictor | Weekly | 1-3 minutes |
| Shrinkage Detector | Weekly | 1-2 minutes |
| Payment Anomaly Detector | Weekly | 1-2 minutes |
| Data Quality Detector | Weekly | 1-2 minutes |
| Payment Distress Classifier | Monthly | 2-5 minutes |
| Rep Performance Predictor | Monthly | 1-3 minutes |
| Credit Limit Predictor | Monthly | 2-5 minutes |
| Credit Classifier | Monthly | 2-5 minutes |

All scheduled 2:00-5:00 AM EAT.

---

## 9. LLM Configuration

### 9.1 Provider Strategy

```
Primary:    Local-only Ollama at http://192.168.2.17:11434 → qwen2.5-coder:32b
Fallback:   DISABLED
```

### 9.2 LLM Usage by Module

| Module | Temperature | Timeout | Max Tokens |
|---|---|---|---|
| Credit Scoring | 0.1 | 30s | 1000 |
| Credit Explainer | 0.2 | 30s | 500 |
| Order Suggestions | 0.3 | 30s | 300 |
| Recommendation Agent | 0.4 | 30s | 2000 |
| Compliance Reporting | 0.3 | 30s | 3000 |
| Synthetic Name Generation | 0.7 | 30s | 500 |

### 9.3 RAG Configuration

- Embedding: nomic-embed-text via Ollama, 768 dimensions, pgvector cosine similarity
- Contexts: credit (5 nearest merchants), recommendations (past outcomes), compliance (approved reports)

### 9.4 Resilience

- Circuit breakers: sliding window 10, 50% threshold, 30s wait
- Timeout: 30s, retry: 3 attempts with exponential backoff
- Fallback: graceful degradation

---

## 10. Event-Driven Integration

### 10.1 AI Event Flow

```
  Payment recorded     → PaymentRecordedEvent  → PaymentAnomalyDetector → Alert
  Stock adjusted       → StockAdjustedEvent     → ShrinkageDetector      → Alert
  Order created        → OrderCreatedEvent       → DataQualityDetector    → Validation
  Merchant onboarded   → MerchantCreatedEvent    → CreditScoringAiService → Evaluation
  Delivery completed   → DeliveryCompletedEvent  → Feature update
  Phase transition     → DataPhaseTransitionEvent → Retraining trigger
```

### 10.2 Event Schema

```java
public record PaymentRecordedEvent(UUID paymentId, UUID merchantId, UUID distributorId,
    BigDecimal amount, String paymentMethod, Instant occurredAt) {}

public record DataPhaseTransitionEvent(String modelName, UUID distributorId,
    DataPhase fromPhase, DataPhase toPhase, long realDataCount) {}
```

---

## 11. API Extensions

### 11.1 REST Endpoints

```
/v1/ai/credit
  POST   /evaluate/{merchantId}
  GET    /evaluations/{merchantId}
  GET    /score/{merchantId}
  POST   /adjust/{merchantId}

/v1/ai/demand
  GET    /forecast/{merchantId}
  GET    /forecast/warehouse/{warehouseId}
  GET    /suggestions/{merchantId}

/v1/ai/anomaly
  GET    /alerts
  PUT    /alerts/{alertId}/acknowledge
  PUT    /alerts/{alertId}/resolve
  GET    /alerts/summary

/v1/ai/prediction
  GET    /stockout/{warehouseId}
  GET    /rep-performance

/v1/ai/routing
  POST   /optimize
  GET    /routes/{date}
  POST   /reoptimize
  GET    /routes/{routeId}

/v1/ai/recommendations
  GET    /{distributorId}
  PUT    /{recommendationId}/accept
  PUT    /{recommendationId}/reject

/v1/ai/reports
  POST   /compliance/generate
  GET    /compliance/{reportId}

/v1/ai/system
  GET    /health
  GET    /models
  GET    /models/{modelName}/performance
  GET    /data-maturity/{distributorId}

/v1/ai/admin
  POST   /train/{modelName}/{distributorId}       # Manual training trigger
```

### 11.2 Authorization

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
| /v1/ai/admin | SUPER_ADMIN |

---

## 12. Monitoring and Observability

### 12.1 Prometheus Metrics

```
# LLM metrics
zuqi_ai_llm_requests_total{provider, model, module}
zuqi_ai_llm_latency_seconds{provider, model, module}
zuqi_ai_llm_errors_total{provider, model, error_type}

# ML model metrics
zuqi_ai_model_inference_total{model_name, model_version}
zuqi_ai_model_inference_latency_ms{model_name}
zuqi_ai_model_training_duration_seconds{model_name}

# Pipeline metrics
zuqi_ai_training_runs_total{model_name, status}
zuqi_ai_feature_computation_duration_seconds{feature_service}
zuqi_ai_forecast_records_generated{date}

# Data maturity metrics
zuqi_ai_data_phase{model_name, distributor_id}
zuqi_ai_real_data_ratio{model_name, distributor_id}
zuqi_ai_real_data_count{model_name, distributor_id}
zuqi_ai_confidence_modifier{model_name, distributor_id}
zuqi_ai_phase_transitions_total{model_name, from_phase, to_phase}

# Route optimization
zuqi_ai_route_solver_duration_seconds
zuqi_ai_route_stops_total{date}

# Alerts
zuqi_ai_alerts_generated_total{alert_type, severity}
zuqi_ai_alerts_resolution_time_hours{alert_type}
```

### 12.2 Grafana Dashboards

**AI Operations:** LLM requests/latency, ML inference, training status, active models
**Model Quality:** Accuracy trends (phase-tagged), drift indicators, business outcomes
**Data Maturity:** Phase progress bars, real data accumulation rate, time-to-next-transition, confidence trends
**Alerts:** Open by type/severity, resolution rates, false positive rates

---

## 13. Infrastructure Deployment

### 13.1 Deployment Architecture

```
┌──────────────────────────────────────────────────────┐
│                   Docker Compose / K8s                │
│                                                      │
│  ┌─────────────────┐    ┌──────────────────────────┐ │
│  │  Zuqi Spring     │    │  Ollama                  │ │
│  │  Boot App        │───▶│  (qwen2.5-coder:32b)    │ │
│  │  (AI + synthetic │    │  at 192.168.2.17:11434   │ │
│  │   in-memory)     │    └──────────────────────────┘ │
│  │                  │                                 │
│  │                  │───▶ PostgreSQL + pgvector        │
│  │                  │───▶ Redis                        │
│  │                  │───▶ GraphHopper (Kenya OSM)      │
│  └─────────────────┘                                  │
│                                                       │
│  ┌─────────────────┐                                  │
│  │  Prometheus +    │                                  │
│  │  Grafana         │                                  │
│  └─────────────────┘                                  │
└───────────────────────────────────────────────────────┘
```

### 13.2 Resource Estimates

| Component | CPU | RAM | GPU | Storage |
|---|---|---|---|---|
| Zuqi Spring Boot (with AI) | 4 vCPU | 16 GB | — | — |
| Ollama (qwen2.5-coder:32b) | 4 vCPU | 16 GB | 24 GB VRAM | 20 GB |
| PostgreSQL + pgvector | 4 vCPU | 16 GB | — | 100 GB+ |
| Redis | 2 vCPU | 8 GB | — | — |
| GraphHopper | 2 vCPU | 4 GB | — | 2 GB |
| Prometheus + Grafana | 2 vCPU | 4 GB | — | 50 GB |

**Note:** In-memory synthetic generation requires ~2-4 GB of heap during training runs. The 16 GB app server allocation handles this comfortably alongside normal operations since training runs during off-peak hours.

### 13.3 Estimated Monthly Cost

| Component | Estimated Cost (Cloud) |
|---|---|
| GPU instance (A10G) | $400-500/month |
| Application server | $150-200/month |
| PostgreSQL (managed) | $100-200/month |
| Redis (managed) | $50-100/month |
| Monitoring | $50-100/month |
| **Total** | **$750-1,100/month** |

---

## 14. Security and Compliance

### 14.1 Data Protection

- **All merchant financial data processed locally** (Ollama) — never sent to external providers
- **Local-only deployment**: zero data leaving controlled infrastructure
- pgvector embeddings: derived features only, not raw PII
- Model registry: model binaries encrypted at rest
- Prediction audit log: retained minimum 7 years (KCB compliance)
- **Synthetic data exists only in-memory during training** — never persisted, never queryable

### 14.2 Kenya Data Protection Act Compliance

- **100% local data processing** within controlled infrastructure
- **Zero cross-border data transfer**
- Merchant consent: credit scoring requires explicit consent
- Right to explanation: `CreditExplainer` provides human-readable reasoning

### 14.3 AI-Specific Security

- LLM prompt injection protection: input sanitization
- Agent tool calls: bounded (max 10 per run), authorization validated
- Model poisoning prevention: training data validated
- Adversarial input detection: extreme feature values flagged

### 14.4 KCB Audit Compliance for Synthetic Data

- Every model version records: `data_phase`, `real_data_ratio`, `synthetic_records_used`, `real_records_used`
- Every prediction records: `data_phase`, `raw_confidence`, `confidence_score` (phase-adjusted)
- Synthetic-phase credit decisions **always route to human review**
- Generation runs logged in `ai_synthetic_runs` with seed values for reproducibility
- Phase transitions logged and auditable

---

*This blueprint defines the complete AI system architecture for Zuqi. All modules are designed to integrate into the existing Spring Boot application with minimal changes to existing code. Synthetic data is generated in-memory during training runs — never persisted — ensuring zero database bloat and zero risk of synthetic data leaking into operations. The architecture supports evolution from synthetic-trained models to real-data models, and from LLM-based solutions to trained ML classifiers, as the system matures.*