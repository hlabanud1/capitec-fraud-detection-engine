FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Download dependencies (cached layer)
RUN ./mvnw -B dependency:go-offline

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw -B clean package -DskipTests

# Production stage
FROM eclipse-temurin:21-jre-alpine

# Image metadata
LABEL org.opencontainers.image.title="Fraud Detection Engine"
LABEL org.opencontainers.image.description="Real-time fraud detection system for financial transactions"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.vendor="Capitec Bank"

WORKDIR /app

# Install wget and create non-root user for security
RUN apk add --no-cache wget \
    && addgroup -g 1001 -S appuser \
    && adduser -u 1001 -S appuser -G appuser

# Copy the built artifact from builder stage
COPY --from=builder /app/target/fraud-detection-engine-1.0.0.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app

USER appuser

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/transactions/health || exit 1

# Run the application
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
