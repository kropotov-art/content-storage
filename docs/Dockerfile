# syntax=docker/dockerfile:1

# ─── Build stage: Alpine with Java 21 and Gradle ───────────────────
FROM alpine/java:21-jdk AS build

# Install Gradle
RUN apk add --no-cache gradle

# Set working directory
WORKDIR /app

# Copy project sources and build files
COPY . .

# Build the fat JAR
RUN gradle clean bootJar --no-daemon


# ─── Package stage: OpenJDK 21 runtime ─────────────────────────────
FROM openjdk:21-jdk-slim

# Create non-root user
RUN groupadd --system appgroup && useradd --system --ingroup appgroup appuser

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Use non-root user
USER appuser:appgroup

# Expose application port
EXPOSE 8080

# Launch the application
ENTRYPOINT ["java", "-jar", "app.jar"]