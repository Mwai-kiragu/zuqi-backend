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
