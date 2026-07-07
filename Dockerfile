# syntax=docker/dockerfile:1

FROM node:22-bookworm-slim AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build -- --configuration production

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /backend
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend /frontend/dist/frontend-app/browser ./src/main/resources/static
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home --home-dir /app --shell /usr/sbin/nologin appuser
COPY --from=backend /backend/target/duo-nakijken-backend-*.jar /app/app.jar
USER appuser
ENV SPRING_PROFILES_ACTIVE=fly
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
