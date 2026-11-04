# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------
# Dependencies are resolved in their own layer, keyed on the POM alone. Editing
# a source file then rebuilds in seconds instead of re-downloading Maven Central.
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY config/ config/
COPY src/ src/

# Tests are not run here. They need a Docker daemon for Testcontainers, which is
# not available inside a build container; CI runs the full suite before this
# image is ever built. Building an image is packaging, not verification.
RUN ./mvnw -B -q clean package -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true \
    && java -Djarmode=tools -jar target/mini-bank.jar extract --layers --destination extracted

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
# A JRE, not a JDK: no compiler, no build tools, a smaller attack surface.
FROM eclipse-temurin:25-jre-alpine AS runtime

# curl is used by the compose healthcheck; tini reaps zombies and forwards
# signals so that SIGTERM reaches the JVM and graceful shutdown actually runs.
RUN apk add --no-cache curl tini \
    && addgroup -S minibank && adduser -S -G minibank -h /app minibank

WORKDIR /app

# Spring Boot's layered extraction, copied one layer per COPY so that Docker can
# cache them independently: the ~95 dependency jars change rarely, the
# application jar changes on every commit, and only the last layer is rebuilt.
#
# The result is a plain (non-fat) jar next to a lib/ directory, which is what
# `extract` produces in Boot 3.5 -- the jar's manifest Class-Path points at
# lib/, so it is launched with a normal `java -jar`. Starting from an already
# expanded layout also avoids the fat-jar's nested-jar indirection at startup.
COPY --from=builder --chown=minibank:minibank /build/extracted/dependencies/ ./
COPY --from=builder --chown=minibank:minibank /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=minibank:minibank /build/extracted/application/ ./

# Never root. A container escape should not start from uid 0.
USER minibank

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=docker

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
# exec, so the JVM becomes PID 1's child directly and receives SIGTERM itself.
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/mini-bank.jar"]
