# syntax=docker/dockerfile:1

# ─── Build stage ─────────────────────────────────────────────────
FROM gradle:8.1.1-jdk21 AS build
WORKDIR /app

# Copy sources and gradle wrapper
COPY --chown=gradle:gradle . .

# Ensure wrapper is executable and build
RUN chmod +x gradlew && \
    ./gradlew clean bootJar --no-daemon


# ─── Package stage ────────────────────────────────────────────────
FROM openjdk:21-jdk-slim

# Create non-root user for security
RUN groupadd --system appgroup && useradd --system --ingroup appgroup appuser

WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Install wget for healthcheck
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Drop privileges
USER appuser:appgroup

# Expose application port
EXPOSE 8080

# Health check endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Launch the application
ENTRYPOINT ["java", "-jar", "app.jar"]
