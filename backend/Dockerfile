# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
# Resolve dependencies in their own layer so code changes don't re-download them.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /var/lib/dms/documents && chown -R app:app /var/lib/dms
USER app
WORKDIR /app
COPY --from=build /workspace/target/dms-backend.jar app.jar
ENV DMS_STORAGE_LOCATION=/var/lib/dms/documents
EXPOSE 8081
HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=10 \
    CMD wget -qO- http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
