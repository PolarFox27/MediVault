# Stage 1: Build
FROM gradle:8.10.2-jdk21 AS build

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle/

# Copy source code
COPY src src/

# Build Spring Boot application
RUN ./gradlew clean bootJar -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the Spring Boot JAR
COPY --from=build /app/build/libs/*.jar app.jar

# CORRECTION: Copier le certificat depuis le contexte (pas depuis build stage)
COPY src/main/resources/medivault.p12 /app/medivault.p12

# Expose HTTPS port
EXPOSE 8443

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]