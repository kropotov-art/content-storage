# syntax=docker/dockerfile:1

# ─── Stage 1: Build the application ──────────────────────────────────
# Use official Gradle image with Java 21 for building
FROM alpine/java:21-jdk AS build
WORKDIR /app

# Copy project files (including Gradle wrapper)
COPY --chown=gradle:gradle . .

# Ensure gradlew wrapper is executable
RUN chmod +x gradlew

# Build the fat JAR using wrapper
RUN ./gradlew clean bootJar --no-daemon


# ─── Stage 2: Create the runtime image ──────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=build /app/build/libs/*.jar app.jar

# Drop privileges
USER appuser:appgroup

# Expose application port
EXPOSE 8080

# Install wget for health check
RUN apk add --no-cache wget

# Health check endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Launch the application
ENTRYPOINT ["java", "-jar", "app.jar"]