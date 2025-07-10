# ─── build stage ─────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Gradle wrapper and build files for dependency resolution
COPY gradlew settings.gradle build.gradle gradle/ ./
RUN chmod +x gradlew

# Download dependencies to cache them
RUN ./gradlew --no-daemon build -x test || true

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew --no-daemon clean bootJar \
    && mv build/libs/*.jar app.jar

# ─── slim runtime stage ──────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
ARG JAR=app.jar
WORKDIR /app

# Install wget for health check
RUN apk add --no-cache wget

# Copy the built JAR from build stage
COPY --from=build /app/app.jar ./

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java","-jar","/app/app.jar"]