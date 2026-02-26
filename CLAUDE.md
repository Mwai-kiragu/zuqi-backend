# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Zuqi is a field sales and supply chain execution platform built with Spring Boot 3.5. It provides order processing, payment integration (KCB Bank, M-Pesa), AI-powered credit scoring, and inventory management for distributors and merchants in Kenya.

## Build and Run Commands

```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run

# Run with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName

# Skip tests during build
./mvnw clean package -DskipTests
```

## Required Services

- **PostgreSQL** with pgvector extension (for AI embeddings)
- **Redis** for caching and session management

Environment variables needed:
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `JWT_SECRET` (min 256 bits for production)
- `OPENAI_API_KEY` (for AI features)

## Architecture

### Package Structure

```
com.zuqi
├── api/                    # REST layer
│   ├── controller/         # REST endpoints (versioned: /v1/*)
│   ├── dto/               # Request/response DTOs
│   └── mapper/            # MapStruct mappers
├── config/                # Spring configuration classes
├── domain/                # JPA entities organized by bounded context
│   ├── user/              # User, Role, Permission, RefreshToken
│   ├── order/             # Order, OrderItem
│   ├── payment/           # Payment, PaymentTransaction
│   ├── merchant/          # Merchant, MerchantCategory
│   ├── distributor/       # Distributor, Warehouse
│   ├── inventory/         # Stock, StockMovement, Product
│   ├── credit/            # CreditScore, CreditLimit, CreditApplication
│   ├── invoice/           # Invoice, InvoiceDiscounting
│   └── sales/             # Route, Delivery
├── exception/             # Custom exceptions and global handler
├── repository/            # Spring Data JPA repositories
├── security/              # JWT and Casbin authorization
├── service/               # Business logic
│   ├── impl/              # Service implementations
│   ├── ai/                # AI-powered services (credit scoring)
│   └── integration/       # External integrations (KCB, M-Pesa)
```

### Key Architectural Decisions

**Authentication & Authorization:**
- JWT-based stateless authentication with access/refresh token pattern
- Casbin (jcasbin) for fine-grained RBAC authorization
- Casbin model at `src/main/resources/casbin/model.conf` uses keyMatch2 for URL patterns
- Authorization filter checks permissions via `CasbinAuthorizationService`

**Database:**
- PostgreSQL with Flyway migrations in `src/main/resources/db/migration/`
- JPA hibernate ddl-auto is `validate` in production, `update` in dev
- Uses UUID for primary keys on most entities
- pgvector for AI embedding storage

**Caching:**
- Redis-backed caching with Spring Cache abstraction
- 1-hour default TTL for cached values

**API Design:**
- All endpoints under `/api` context path (configured in application.yml)
- API versioning via URL path prefix (`/v1/`)
- Standard response wrapper: `ApiResponse<T>` with success/error handling
- OpenAPI/Swagger UI at `/api/swagger-ui.html`

**Resilience:**
- Resilience4j circuit breaker configured for AI service calls
- 30s timeout, 50% failure threshold, exponential backoff retry

### Domain Model Highlights

- **Multi-tenant by Distributor**: Most entities are scoped to a distributor
- **Merchants** are retail outlets that place orders and can have credit limits
- **Sales Reps** are assigned routes and manage merchant relationships
- **Orders** flow through: PENDING -> CONFIRMED -> PROCESSING -> DELIVERED
- **Credit Scoring** uses AI (GPT-4) to evaluate merchant creditworthiness

## AI Integration Architecture

**IMPORTANT:** Zuqi is implementing a comprehensive AI layer. Before working on AI features, always consult these documents:

### Planning Documents

1. **`plan.md`** - Complete AI System Architecture Blueprint
   - Defines all 12 AI capabilities and their technical architecture
   - Technology stack specifications (LangChain4j, Tribuo, Timefold, GraphHopper)
   - Package structure under `com.zuqi.ai`
   - Database schema for AI tables
   - Feature engineering layer design
   - Model management and registry architecture
   - LLM configuration and RAG infrastructure
   - Event-driven integration patterns

2. **`implementation_plan.md`** - Phased Implementation Guide
   - 6-phase execution plan with clear dependencies
   - Task-by-task breakdown with "Definition of Done" criteria
   - Phase 1: Foundation Infrastructure (feature engineering, model registry)
   - Phase 2: LLM Integration & Credit Scoring
   - Phase 3: Classical ML - Demand Forecasting
   - Phase 4: Anomaly Detection & Predictive Alerts
   - Phase 5: Route Optimization
   - Phase 6: AI Agent, Reporting & Evolution
   - Verification checklists for each phase

### AI Package Structure

All AI code lives under `com.zuqi.ai` with the following modules:

```
com.zuqi.ai/
├── config/          # LangChain4j, Tribuo, Timefold, GraphHopper config
├── feature/         # Feature engineering services (foundation layer)
├── model/           # ML model registry, loader, trainer, evaluator
├── credit/          # Credit risk scoring (LLM → ML evolution)
├── demand/          # Demand forecasting and order suggestions
├── anomaly/         # Shrinkage, payment anomaly, data quality detection
├── prediction/      # Stockout prediction, rep performance monitoring
├── routing/         # Route optimization (Timefold + GraphHopper)
├── agent/           # AI agent for operational recommendations
├── reporting/       # Compliance reporting with LLM
├── pipeline/        # Training pipeline orchestration (Spring Batch)
└── monitoring/      # Model drift detection, performance tracking
```

### AI Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| LLM Framework | LangChain4j | LLM chains, agents, RAG, structured output |
| Local LLM Runtime | Ollama | Hosts Qwen 2.5 32B / Mixtral 8x7B |
| Cloud LLM (fallback) | GPT-4 / Claude | Complex reasoning, fallback |
| Classical ML | Tribuo (Oracle) | XGBoost, Isolation Forest, regression, classification |
| Optimization Solver | Timefold | Vehicle routing optimization |
| Road Network | GraphHopper | Kenya road network distance/time matrix |
| Embedding Storage | PostgreSQL + pgvector | RAG context retrieval |

### 12 AI Capabilities

1. **Credit Risk Scoring** - LLM-based evaluation evolving to ML classifier
2. **AI-Powered Order Suggestions** - ML regression + optional LLM enhancement
3. **Demand Forecasting** - XGBoost regression for merchant-SKU predictions
4. **Dynamic Route Optimization** - Timefold solver for vehicle routing
5. **Inventory Shrinkage Detection** - Isolation Forest anomaly detection
6. **Payment Anomaly Detection** - Isolation Forest + distress classification
7. **Stockout Prediction** - XGBoost classification
8. **Sales Rep Underperformance** - XGBoost regression with alerting
9. **Dynamic Credit Limit Adjustment** - XGBoost regression with business rules
10. **Operational Recommendations** - LangChain4j agent with 7 data tools
11. **Compliance Reporting** - LLM-powered narrative generation
12. **Data Quality Detection** - Rules engine + ML anomaly detection

### AI Database Tables

AI features use these **9 additional tables** (created via Flyway migrations V15-V23):

- `ai_model_registry` - Model versioning, metadata, binaries, performance metrics (with distributor support)
- `ai_predictions` - Audit log for all predictions with input hash, overrides, and retention policies
- `ai_model_performance` - Time-series tracking of model accuracy with unique constraints
- `ai_demand_forecasts` - Pre-computed demand predictions per merchant-SKU with foreign keys
- `ai_anomaly_alerts` - Centralized alert management with status workflow and comprehensive indexing
- `ai_recommendations` - Agent-generated operational recommendations with outcome tracking
- `ai_delivery_routes` - Optimized route plans with vehicle metadata and driver assignments
- `ai_merchant_embeddings` - pgvector embeddings for RAG similarity search (credit scoring context)
- `ai_recommendation_embeddings` - Past recommendations embedded for agent context retrieval

**Schema Highlights:**
- All tables have proper foreign key constraints to existing entities (merchants, distributors, users, products)
- Multi-tenant isolation via `distributor_id` columns where applicable
- Comprehensive indexing for query performance (30+ indexes across all tables)
- JSONB columns for flexible metadata storage (evidence, context, hyperparameters)
- pgvector indexes for similarity search using cosine distance (IVFFlat with 100 lists)
- Data retention support via `expires_at` columns (GDPR compliance)
- Complete audit trails with created_at/updated_at timestamps
- Unique constraints prevent duplicate data (metrics, forecasts, embeddings)

### Key Architectural Patterns

**Feature Engineering First:**
- All AI modules consume features from centralized feature services
- Same feature computation logic for training and inference (consistency guarantee)
- Caching via Redis with appropriate TTLs
- Historical mode for training, current mode for real-time inference

**Model Lifecycle:**
- TRAINING → EVALUATING → ACTIVE → RETIRED
- Only one ACTIVE version per model at a time
- Hot-swappable: new models promoted without restart
- Quality gates prevent regression

**Event-Driven AI:**
- `PaymentRecordedEvent` → Payment anomaly detection
- `StockAdjustedEvent` → Shrinkage detection
- `OrderCreatedEvent` → Data quality validation
- `MerchantCreatedEvent` → Credit evaluation
- Async execution to avoid blocking transactions

**LLM Strategy:**
- Primary: Ollama (local) for data privacy and cost
- Fallback: Cloud API (GPT-4/Claude) for complex reasoning
- RAG via pgvector for context-aware responses
- Resilience4j circuit breaker protection

**Evolution Path:**
- Phase 1-2: LLM-based solutions for immediate capability
- Phase 3-6: Migrate to trained ML models as data accumulates
- Hybrid scoring during transition (LLM + ML comparison)

### Working with AI Features

**When implementing AI features:**

1. **Always check the planning documents first** - `plan.md` for architecture, `implementation_plan.md` for tasks
2. **Follow the phase order** - Don't skip dependencies
3. **Use the feature engineering layer** - Never bypass `FeatureStore`
4. **Log all predictions** - Required for KCB partnership compliance
5. **Test with fallbacks** - Handle missing models gracefully
6. **Monitor performance** - Add Prometheus metrics for new AI operations

**When adding new AI endpoints:**
- Place controllers under `com.zuqi.ai` package
- Follow pattern: `/v1/ai/{module}/{action}`
- Add Casbin RBAC rules in `policy.csv`
- Document in OpenAPI/Swagger

**When creating new models:**
- Register in `ai_model_registry` via `ModelRegistry` service
- Implement training pipeline using Spring Batch template
- Define quality gates in `ModelPromotionStep`
- Add drift detection monitoring

### AI Development Environment

Additional environment variables for AI features:
- `OLLAMA_BASE_URL` - Ollama API endpoint (configured: http://192.168.2.17:11434 — network machine, not localhost)
- `OLLAMA_MODEL` - Local LLM model name (configured: qwen2.5-coder:32b)
- `OPENAI_API_KEY` - Cloud LLM fallback (already configured)
- `ANTHROPIC_API_KEY` - Claude API fallback (optional)

## Testing

- Uses H2 in-memory database for tests
- Spring Security Test for authentication testing
- Tests should be added to `src/test/java/com/zuqi/`

## Frontend Integration

The frontend (React + Vite) is at `../zuqi-frontend/` and runs on port 3000.

- Vite proxies `/api/*` requests to `http://localhost:8080`
- CORS is configured to allow `localhost:3000` and `localhost:5173`
- Use `VITE_API_BASE_URL` env var for API base URL in frontend

## Local Development Setup

```bash
# Check services are running
./start-dev.sh check

# Start backend (requires PostgreSQL + Redis)
cd zuqi-backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Start frontend (in separate terminal)
cd zuqi-frontend && npm run dev
```

**URLs:**
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api
- Swagger UI: http://localhost:8080/api/swagger-ui.html
