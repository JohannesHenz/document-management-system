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
# Tesseract with English and German models; fonts for rendering PDF pages.
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng tesseract-ocr-data-deu fontconfig ttf-dejavu \
    && addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /workspace/target/dms-ocr-worker.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
