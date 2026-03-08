# ── Stage 1: Build ────────────────────────────────────────────────────────────
# Full JDK 25 image to compile and package the application
FROM eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /app

# Copy Maven wrapper and POM first — allows Docker to cache the dependency
# layer and skip re-downloading on source-only changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -q

# Copy source and build the fat JAR (tests run in CI, not at image build time)
COPY src ./src
RUN ./mvnw package -B -q -DskipTests

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# Minimal JRE-only image — no compiler, no build tools, no shell in PATH.
# Google Distroless does not yet publish a Java 25 variant (latest is Java 21
# LTS). eclipse-temurin:25-jre-jammy is the smallest official JRE 25 image
# and follows the same minimal-attack-surface philosophy.
FROM eclipse-temurin:25-jre-jammy

# Run as a dedicated non-root system user
RUN groupadd --system appgroup \
 && useradd --system --gid appgroup --no-create-home appuser

WORKDIR /app

# Copy only the executable JAR from the build stage
COPY --from=builder /app/target/*.jar app.jar

# H2 writes its file-based database here — mount a named volume to persist data
VOLUME ["/app/data"]

EXPOSE 8080
USER appuser

# Enable container-aware memory limits; cap heap at 75% of container RAM
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
