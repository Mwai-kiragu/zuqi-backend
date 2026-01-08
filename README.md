# Zuqi Backend

Spring Boot REST API for the Zuqi B2B Distribution Platform.

## Requirements

- Java 21
- PostgreSQL 15+
- Redis 7+

## Setup

### 1. Create Database

```bash
psql -U postgres -c "CREATE USER zuqi WITH PASSWORD 'zuqi';"
psql -U postgres -c "CREATE DATABASE zuqi OWNER zuqi;"
```

### 2. Set Environment Variables

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=zuqi
export DB_USERNAME=zuqi
export DB_PASSWORD=zuqi
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

# 3. Run Application

```bash
./mvnw spring-boot:run
```

API runs at: http://localhost:8080/api

# Test Users

| Role | Email | Password |
|------|-------|----------|
| ADMIN | admin@zuqi.com | Password123 |
| DISTRIBUTOR_ADMIN | distributor@zuqi.com | Password123 |
| SALES_REP | sales@zuqi.com | Password123 |
| WAREHOUSE_MANAGER | warehouse@zuqi.com | Password123 |
| MERCHANT | merchant@zuqi.com | Password123 |
| FINANCE | finance@zuqi.com | Password123 |
| DRIVER | driver@zuqi.com | Password123 |

# Key URLs

- **API:** http://localhost:8080/api
- **Swagger:** http://localhost:8080/api/swagger-ui.html
- **Health:** http://localhost:8080/api/actuator/health
