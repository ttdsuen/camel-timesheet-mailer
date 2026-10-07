# syntax=docker/dockerfile:1

# ---- build stage -------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the wrapper and pom first so dependency resolution is cached separately
# from the source.
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# ---- runtime stage -----------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run as an unprivileged user; the mounted data volume is the only writable place.
RUN useradd --system --uid 1001 --create-home appuser \
    && mkdir -p /app/data \
    && chown -R appuser:appuser /app
USER appuser

COPY --from=build --chown=appuser:appuser /workspace/target/camel-timesheet-mailer-*.jar app.jar

# Writable working directory for the template, outbox and dead-letter folders.
VOLUME ["/app/data"]
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
