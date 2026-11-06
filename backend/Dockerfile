# StockPulse backend image (multi-stage). Build context is the repo root (docker-compose.yml) so the
# API contract (contracts/openapi.yaml) can be packaged for Swagger UI.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src/backend
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY contracts /src/contracts
COPY backend/src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /app/logs && chown -R app:app /app
WORKDIR /app
COPY --from=build /src/backend/target/stockpulse-backend-*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
