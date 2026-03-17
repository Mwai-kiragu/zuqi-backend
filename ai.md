# Zuqi AI Capabilities — Full Implementation Plan

## Context

The Zuqi strategy document defines 4 AI capabilities: credit risk scoring, demand forecasting, route optimization, and anomaly detection. The current codebase has **zero AI implementation** — Spring AI is in pom.xml but unused, no scoring logic, no forecasting, no optimization. Several DB tables exist without JPA entities (credit_applications, routes, deliveries, ai_configurations, payment_transactions).

This plan replaces **Spring AI + OpenAI with Langchain4j + Ollama** (local LLMs for privacy, cost, latency) and implements all 4 capabilities. Each capability has a rule-based/algorithmic core that works without the LLM, plus LLM enrichment for natural language analysis and explanations.

---

## Step 0: Foundation — Dependencies + Missing Entities + New Tables

### 0a: pom.xml — Swap Spring AI for Langchain4j

**Modify `pom.xml`:**
- Remove: `spring-ai-openai-spring-boot-starter` (line 69-74), `spring-ai-bom` from dependencyManagement (lines 175-185), `spring-milestones` repository (lines 229-238), `spring-ai.version` property (line 23)
- Add:
```xml
<langchain4j.version>1.0.0-beta1</langchain4j.version>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama-spring-boot-starter</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

### 0b: application.yml — Replace spring.ai with langchain4j.ollama

- Remove `spring.ai.openai` block (lines 79-86)
- Add:
```yaml
langchain4j:
  ollama:
    ch/192.168.2.17:11434}
      model-name: ${OLLAMA_MODEL:qwen2.5-coder:32b}
      temperature: 0.3
      timeout: PT60S

zuqi:
  credit:
    scoring:
      weights:
        paymentHistory: 0.35
        orderHistory: 0.30
        businessProfile: 0.20
        creditUtilization: 0.15
      auto-approve-threshold: 70.0
      auto-decline-threshold: 40.0
      score-validity-days: 30
      model-version: "rule-v1.0"
      rule-weight: 0.80
      llm-weight: 0.20
      llm-max-adjustment: 20.0
```

### 0c: JPA Entities for Existing Orphaned Tables

DB tables exist (V1 migration) but have no Java entities:

| Entity | Package | Table | Key Fields |
|--------|---------|-------|------------|
| `Route` | `domain.sales` | `routes` | distributor, name, assignedRep, schedule (JSONB), active |
| `Delivery` | `domain.sales` | `deliveries` | order, driver, vehicleInfo, scheduledDate, actualDeliveryDate, proofOfDeliveryUrl, status |
| `PaymentTransaction` | `domain.payment` | `payment_transactions` | payment, transactionType, amount, status, gatewayResponse (JSONB) |
| `AiConfiguration` | `domain.ai` | `ai_configurationsat-model:
      base-url: ${OLLAMA_BASE_URL:http:/` | configKey, configValue (JSONB), category, active |
| `CreditApplication` | `domain.credit` | `credit_applications` | merchant, distributor, requestedAmount, approvedAmount, status, creditScore FK, reviewedBy |
| `CreditApplicationStatus` | `domain.credit` | — | Enum: PENDING, AUTO_APPROVED, ESCALATED, MANUALLY_APPROVED, AUTO_DECLINED, MANUALLY_DECLINED |

Repositories for each: `RouteRepository`, `DeliveryRepository`, `PaymentTransactionRepository`, `AiConfigurationRepository`, `CreditApplicationRepository`

### 0d: Flyway Migration — New AI Tables

**New file: `V15__add_ai_capability_tables.sql`**

```sql
-- Demand forecast results
CREATE TABLE demand_forecasts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    distributor_id UUID NOT NULL REFERENCES distributors(id),
    merchant_id UUID REFERENCES merchants(id),
    product_id UUID NOT NULL REFERENCES products(id),
    warehouse_id UUID REFERENCES warehouses(id),
    forecast_date DATE NOT NULL,
    predicted_quantity DECIMAL(15, 3) NOT NULL,
    confidence_lower DECIMAL(15, 3),
    confidence_upper DECIMAL(15, 3),
    confidence_level DECIMAL(5, 2),
    method VARCHAR(50) NOT NULL,
    factors JSONB,
    model_version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Route optimization results
CREATE TABLE route_optimizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    distributor_id UUID NOT NULL REFERENCES distributors(id),
    route_id UUID REFERENCES routes(id),
    optimization_date DATE NOT NULL,
    original_distance_km DECIMAL(10, 2),
    optimized_distance_km DECIMAL(10, 2),
    estimated_savings_percent DECIMAL(5, 2),
    stop_sequence JSONB NOT NULL,
    total_stops INTEGER NOT NULL,
    ai_summary TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PROPOSED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Anomaly detection alerts
CREATE TABLE anomaly_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    distributor_id UUID NOT NULL REFERENCES distributors(id),
    anomaly_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    raw_data JSONB,
    ai_analysis TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    acknowledged_by UUID REFERENCES users(id),
    acknowledged_at TIMESTAMP,
    resolved_by UUID REFERENCES users(id),
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```
Plus indexes on distributor_id, type, severity, status, entity for each table.

### 0e: Config + Scheduling + Redis Caches

- `config/CreditScoringProperties.java` — `@ConfigurationProperties(prefix = "zuqi.credit.scoring")`
- `config/SchedulingConfig.java` — `@EnableScheduling`
- **Modify `config/RedisConfig.java`** — add cache names: `demand-forecasts` (6hr), `route-optimizations` (2hr), `anomaly-reports` (1hr)
- **Modify `repository/DistributorRepository.java`** — add `findByActiveTrue()`

---

## Capability 1: Credit Risk Scoring (Steps 1-5)

### Step 1: Feature Engineering

**New:** `service/FeatureEngineeringService.java` (interface) + `service/impl/FeatureEngineeringServiceImpl.java`

Computes `MerchantCreditFeatures` record from transactional data:
- **Payment behavior:** onTimePaymentRate, avgDaysToPay, paymentConsistency (stddev), longestOnTimeStreak
- **Order patterns:** frequency/month, avgOrderValue, trend (GROWING/STABLE/DECLINING), cancellationRate, uniqueSkuCount
- **Financial position:** creditUtilizationRatio, outstandingBalance, totalRevenueGenerated
- **Relationship:** monthsAsCustomer, verified, categoryRiskProfile
- **Recency:** daysSinceLastOrder, daysSinceLastPayment

**Modify repositories** — add aggregation queries:
- `OrderRepository`: +6 queries (count by merchant, sum revenue, avg order value, last order date, recent orders for trend)
- `PaymentRepository`: +3 queries (count completed, last payment date, completed payments list)
- `OrderItemRepository`: +1 query (distinct product count by merchant)
- `InvoiceRepository`: +2 queries (overdue count, outstanding balance sum)

### Step 2: Rule-Based Scoring Engine

**New:** `service/ai/RuleBasedScoringEngine.java` — `@Component`

Returns `RuleBasedScoreResult` (score 0-100, grade A-F, factor breakdown). Weights from `CreditScoringProperties`. Sub-scores:
- paymentHistory (35%): onTimeRate, daysToPay, consistency, streak
- orderHistory (30%): frequency, avgValue, trend, cancellation, SKU diversity
- businessProfile (20%): tenure, verified, categoryRisk, recency
- creditUtilization (15%): bell curve optimal at 30-60%

Fully deterministic. Grade: A>=80, B>=65, C>=50, D>=35, F<35.

### Step 3: LLM Chain (Langchain4j)

**New Langchain4j @AiService interfaces:**
- `service/ai/CreditSignalExtractor.java` — extracts `QualitativeSignals` (risk/opportunity signals, sentimentScore) from merchant metadata. Structured JSON output via Langchain4j
- `service/ai/CreditMemoGenerator.java` — generates human-readable credit memo for bank review

**New:** `service/ai/CreditAiService.java` — wraps both @AiServices with `@CircuitBreaker(name = "aiService")` + `@Retry`. Fallbacks: empty signals / template memo.

**Prompt files:** `resources/prompts/credit-signal-extraction.txt`, `resources/prompts/credit-memo-generation.txt`

### Step 4: Pipeline Orchestrator + Decision Engine

**New:** `service/ai/CreditScoringPipelineService.java`

`scoreMerchant(merchantId, distributorId)`:
1. Compute features → 2. Rule-based score → 3. LLM signals (fallback: empty) → 4. Compose final score (80% rule + 20% LLM) → 5. Decision (>=70 AUTO_APPROVED, 40-69 ESCALATED, <40 AUTO_DECLINED) → 6. Recommended limit (avgOrderValue * frequency * 2) → 7. Persist CreditScore + CreditApplication → 8. Generate memo

### Step 5: API + Batch Job

**Modify `CreditController.java`** — add 3 endpoints:
- `POST /v1/credit/score/{merchantId}` — trigger scoring
- `GET /v1/credit/applications` — list with filters
- `GET /v1/credit/applications/{id}` — single application

**Modify `CreditService.java` + `CreditServiceImpl.java`** — add application CRUD methods

**New:** `service/ai/CreditScoringBatchJob.java` — `@Scheduled(cron = "0 0 2 * * *")` nightly, pages through active merchants

---

## Capability 2: Demand Forecasting (Steps 6-8)

### Step 6: Domain + Repository

**New entity:** `domain/ai/DemandForecast.java` — maps to new `demand_forecasts` table. Fields: distributor, merchant (optional), product, warehouse, forecastDate, predictedQuantity, confidenceLower/Upper/Level, method (LLM/MOVING_AVG), factors (JSONB), modelVersion

**New:** `repository/DemandForecastRepository.java` — queries by distributor+product+dateRange, by merchant+product+dateRange

**Modify `OrderRepository.java`** — add 2 native queries:
- `findWeeklyDemandByDistributor()` — GROUP BY product_id, merchant_id, DATE_TRUNC('week')
- `findWeeklyDemandByMerchant()` — same scoped to merchant

### Step 7: Service Layer

**DTOs** (`api/dto/forecast/`):
- `DemandForecastRequest` — distributorId, merchantId (optional), productId, forecastDays (7/14/30)
- `DemandForecastResponse` — forecast details
- `ForecastSummaryResponse` — dailyForecasts list, totalPredicted, currentStock, suggestedReorder, llmAnalysis

**Langchain4j @AiService:** `service/ai/DemandForecastAiService.java`
- Takes weekly historical data, merchant context, current stock, lead time
- Returns structured JSON: dailyForecasts, trend, seasonalityDetected, keyFactors, reorderRecommendation, analysis

**Service:** `service/DemandForecastService.java` (interface) + `service/impl/DemandForecastServiceImpl.java`
- Primary: LLM forecast via Langchain4j (wrapped with `@CircuitBreaker`)
- Fallback: Simple Moving Average (4-week SMA ÷ 7 for daily)
- Persists DemandForecast entities, caches in `demand-forecasts` Redis cache

### Step 8: API + Batch Job

**New controller:** `api/controller/ForecastController.java`
- `POST /v1/forecasts/generate` — single product forecast
- `GET /v1/forecasts` — list existing forecasts
- `POST /v1/forecasts/batch` — all active products for a distributor

**New:** `service/ai/DemandForecastScheduler.java` — `@Scheduled(cron = "0 0 3 * * *")` daily at 3 AM, generates 14-day forecasts for all active distributors

---

## Capability 3: Route Optimization (Steps 9-11)

### Step 9: Domain + Repository

**New entity:** `domain/ai/RouteOptimization.java` — maps to new `route_optimizations` table. Fields: distributor, route (optional), optimizationDate, originalDistanceKm, optimizedDistanceKm, estimatedSavingsPercent, stopSequence (JSONB), totalStops, aiSummary, status (PROPOSED/ACCEPTED/IN_PROGRESS/COMPLETED)

**New enums:** `domain/sales/DeliveryStatus.java` — PENDING, ASSIGNED, EN_ROUTE, DELIVERED, FAILED, RETURNED

**New:** `repository/RouteOptimizationRepository.java` — by distributor+date, by route+date, history with pagination

### Step 10: Service Layer

**DTOs** (`api/dto/route/`):
- `RouteOptimizationRequest` — distributorId, routeId (optional), driverId (optional), date, warehouseId
- `RouteStop` — sequence, merchantId, merchantName, lat/lng, address, orderId, distanceFromPrevKm
- `RouteOptimizationResponse` — stops, distances, savings, aiSummary, status

**Core algorithm:** `service/ai/RouteOptimizer.java` — `@Component`, pure Java
- **NOT an LLM task** — combinatorial optimization
- Phase 1: Greedy nearest-neighbor TSP construction from warehouse depot
- Phase 2: 2-opt improvement (reverse segments to reduce total distance)
- Uses Haversine formula for great-circle distance
- Helper record: `GeoPoint(double lat, double lng)`

**Langchain4j @AiService:** `service/ai/RouteExplanationAiService.java`
- Takes optimized route details (stops, distances, savings)
- Returns 2-3 sentence natural language summary for drivers/dispatchers

**Service:** `service/RouteOptimizationService.java` (interface) + `service/impl/RouteOptimizationServiceImpl.java`
- Loads warehouse as depot + pending deliveries/merchants for the date
- Runs RouteOptimizer algorithm
- LLM generates route summary (fallback: template text)
- Persists RouteOptimization entity

### Step 11: API

**New controller:** `api/controller/RouteOptimizationController.java`
- `POST /v1/routes/optimize` — optimize a route
- `GET /v1/routes/optimize/{id}` — get optimization result
- `GET /v1/routes/optimize` — history with pagination
- `PATCH /v1/routes/optimize/{id}/accept` — accept optimization

No batch job — route optimization is on-demand (triggered before daily dispatch).

---

## Capability 4: Anomaly Detection (Steps 12-15)

### Step 12: Domain + Repository

**New entity:** `domain/ai/AnomalyAlert.java` — maps to new `anomaly_alerts` table. Fields: distributor, anomalyType, severity, entityType, entityId, title, description, rawData (JSONB), aiAnalysis, status, acknowledgedBy, resolvedBy, resolutionNotes, timestamps

**New enums** (`domain/ai/`):
- `AnomalyType` — INVENTORY, SALES, PAYMENT, ROUTE
- `Severity` — LOW, MEDIUM, HIGH, CRITICAL
- `AlertStatus` — OPEN, ACKNOWLEDGED, INVESTIGATING, RESOLVED, DISMISSED

**New:** `repository/AnomalyAlertRepository.java` — filters by distributor+type+status, active high-severity alerts, count by type, duplicate prevention (findExistingAlert within 24hr window)

**Modify repositories** — add queries for anomaly baselines:
- `StockMovementRepository`: +1 native query for daily movement totals by product/warehouse
- `PaymentRepository`: +2 queries (merchant payments since date, distributor payments in range)
- `OrderRepository`: +1 native query for daily sales by merchant

### Step 13: Rule-Based Anomaly Detectors

**Interface:** `service/ai/detectors/AnomalyDetector.java` — `getType()` + `detect(UUID distributorId)` returns `List<RawAnomaly>`

**Helper DTO:** `service/ai/detectors/RawAnomaly.java` — anomalyType, severity, entityType, entityId, title, description, rawData

**3 Detectors:**

| Detector | What It Detects |
|----------|-----------------|
| `InventoryAnomalyDetector` | Stock drops without OUT movements (shrinkage), unusually large ADJUSTMENT movements (z-score > 2.5), expected vs actual stock discrepancies |
| `SalesAnomalyDetector` | Merchant order volume spikes/drops vs 7-day rolling average (z-score > 3.0), unusual discount patterns |
| `PaymentAnomalyDetector` | Payment amounts deviating from merchant patterns (z-score > 2.0), high failed payment frequency, growing unpaid balances exceeding credit limit thresholds |

All use z-score thresholds with configurable values from `ai_configurations` table.

### Step 14: LLM Enrichment + Service

**Langchain4j @AiService:** `service/ai/AnomalyAnalysisAiService.java`
- Takes detected anomaly details (type, severity, entity, description, raw data)
- Returns 3-5 sentence analysis: root cause, business impact, recommended action, follow-up steps

**DTOs** (`api/dto/anomaly/`):
- `AnomalyAlertResponse` — alert details with resolved entity names
- `AnomalyAlertUpdateRequest` — action (ACKNOWLEDGE/INVESTIGATE/RESOLVE/DISMISS) + notes
- `AnomalySummaryResponse` — totalOpen, countByType, countBySeverity, recentCritical list

**Service:** `service/AnomalyDetectionService.java` (interface) + `service/impl/AnomalyDetectionServiceImpl.java`
- Injects all `AnomalyDetector` implementations via `List<AnomalyDetector>` (Spring auto-collects)
- Runs each detector, deduplicates against existing open alerts (24hr window)
- Enriches each alert with LLM analysis (try/catch, analysis is optional)
- Persists AnomalyAlert entities

### Step 15: API + Batch Job

**New controller:** `api/controller/AnomalyController.java`
- `POST /v1/anomalies/detect` — trigger on-demand scan
- `GET /v1/anomalies/summary` — dashboard summary (counts by type/severity)
- `GET /v1/anomalies` — list alerts with filters (type, status, pagination)
- `GET /v1/anomalies/{id}` — alert details
- `PATCH /v1/anomalies/{id}` — update status (acknowledge, resolve, dismiss)

**New:** `service/ai/AnomalyDetectionScheduler.java` — `@Scheduled(cron = "0 0 */6 * * *")` every 6 hours, runs detection for all active distributors

---

## Step 16: Casbin Policies

**New migration: `V16__add_ai_casbin_policies.sql`**

Policies for all 4 capabilities' endpoints, granting access to DISTRIBUTOR_ADMIN, WAREHOUSE_MANAGER, FINANCE, SALES_REP as appropriate per endpoint.

---

## Files Summary

### New files (~55)

| Category | Count | Key Files |
|----------|-------|-----------|
| **Entities** | 8 | CreditApplication, Route, Delivery, PaymentTransaction, AiConfiguration, DemandForecast, RouteOptimization, AnomalyAlert |
| **Enums** | 5 | CreditApplicationStatus, DeliveryStatus, AnomalyType, Severity, AlertStatus |
| **Repositories** | 8 | One per new entity |
| **DTOs** | 16 | Credit (6), Forecast (3), Route (3), Anomaly (3), RawAnomaly (1) |
| **Langchain4j @AiServices** | 5 | CreditSignalExtractor, CreditMemoGenerator, DemandForecastAiService, RouteExplanationAiService, AnomalyAnalysisAiService |
| **Service interfaces** | 4 | FeatureEngineering, DemandForecast, RouteOptimization, AnomalyDetection |
| **Service implementations** | 4 | One per interface |
| **AI services** | 3 | CreditAiService, CreditScoringPipelineService, RuleBasedScoringEngine |
| **Detectors** | 4 | Interface + Inventory, Sales, Payment detectors |
| **Algorithm** | 1 | RouteOptimizer (nearest-neighbor + 2-opt) |
| **Controllers** | 3 | ForecastController, RouteOptimizationController, AnomalyController |
| **Schedulers** | 3 | CreditScoringBatchJob, DemandForecastScheduler, AnomalyDetectionScheduler |
| **Config** | 2 | CreditScoringProperties, SchedulingConfig |
| **Migrations** | 2 | V15 (tables), V16 (casbin policies) |
| **Prompt files** | 2 | credit-signal-extraction.txt, credit-memo-generation.txt |

### Modified files (10)

| File | Changes |
|------|---------|
| `pom.xml` | Remove Spring AI, add Langchain4j + Ollama |
| `application.yml` | Remove spring.ai, add langchain4j.ollama + zuqi.credit.scoring |
| `config/RedisConfig.java` | +3 cache names |
| `repository/OrderRepository.java` | +8 queries (6 credit + 2 forecast) + 1 anomaly |
| `repository/PaymentRepository.java` | +5 queries (3 credit + 2 anomaly) |
| `repository/OrderItemRepository.java` | +1 query |
| `repository/InvoiceRepository.java` | +2 queries |
| `repository/StockMovementRepository.java` | +1 query |
| `repository/DistributorRepository.java` | +1 query (findByActiveTrue) |
| `api/controller/CreditController.java` | +3 endpoints |
| `service/CreditService.java` + `impl/CreditServiceImpl.java` | +2 methods |

---

## Verification

1. **Build:** `./mvnw clean package -DskipTests`
2. **Credit scoring (no Ollama):** Mock CreditAiService, verify rule-based scoring produces deterministic results, fallback works
3. **Credit scoring (with Ollama):** `POST /api/v1/credit/score/{merchantId}` → verify score, grade, decision, memo
4. **Demand forecast:** `POST /api/v1/forecasts/generate` → verify daily predictions, fallback to SMA when Ollama is down
5. **Route optimization:** `POST /api/v1/routes/optimize` → verify stop ordering, distance calculation, savings percent
6. **Anomaly detection:** `POST /api/v1/anomalies/detect` → verify alerts generated for test data anomalies
7. **Batch jobs:** Verify 3 schedulers run without errors (can test with `@Scheduled(fixedDelay = ...)` override)
8. **Casbin:** Verify role-based access to all new endpoints

## Prerequisites
- Ollama installed and running: `ollama serve`
- Model pulled: `ollama pull llama3.1`
- PostgreSQL + Redis running
