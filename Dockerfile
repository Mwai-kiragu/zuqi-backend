# ============================================================
# Zuqi Backend — Multi-stage build
# Java 21 · Spring Boot 3.5
# ============================================================

# ── Stage 1: Build ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Maven wrapper first — cached until pom.xml changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Download all dependencies into a cache layer
RUN ./mvnw dependency:go-offline -q

# Copy source and build the fat JAR
COPY src/ src/
RUN ./mvnw clean package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install libgomp required by XGBoost4j
RUN apk add --no-cache libgomp

# Non-root user for security
RUN addgroup -S zuqi && adduser -S zuqi -G zuqi

COPY --from=builder /build/target/zuqi-*.jar app.jar

RUN mkdir -p uploads && chown -R zuqi:zuqi /app
USER zuqi

# Respect container CPU/memory limits (Java 21 container-aware by default)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
