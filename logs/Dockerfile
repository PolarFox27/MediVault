# Stage 1: Build
FROM gradle:8.10.2-jdk21 AS build

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle/

# Copy source code
COPY src src/

# Build fat JAR (utilise la tâche fatJar définie dans build.gradle)
RUN ./gradlew clean fatJar -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the fat JAR (nom avec -all suffix)
COPY --from=build /app/build/libs/*-all.jar ./logserver.jar

# Expose port
EXPOSE 5555

# Run the application
ENTRYPOINT ["java", "-jar", "logserver.jar"]