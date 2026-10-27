# Multi-stage Dockerfile for Meloo Backend (Spring Boot 4.x, Java 21)
# Stage 1: Build application with Maven
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Install bash for Maven wrapper script execution
RUN apk add --no-cache bash

# Copy contracts (required for openapi-generator) and backend wrapper
COPY contracts/ /workspace/contracts/
COPY backend/.mvn/ /workspace/backend/.mvn/
COPY backend/mvnw /workspace/backend/mvnw
COPY backend/pom.xml /workspace/backend/pom.xml

WORKDIR /workspace/backend
RUN chmod +x ./mvnw

# Copy source code and build jar without running tests (tests run in CI verify)
COPY backend/src/ /workspace/backend/src/
RUN ./mvnw clean package -DskipTests --batch-mode --no-transfer-progress

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install wget for container healthcheck and create non-root user
RUN apk add --no-cache wget && \
    addgroup -S meloo && adduser -S meloo -G meloo

COPY --from=builder --chown=meloo:meloo /workspace/backend/target/meloo-backend-*.jar app.jar

USER meloo

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
