# Use a lightweight OpenJDK image
FROM eclipse-temurin:21-jdk-jammy

# Set working directory inside container
WORKDIR /app

# Copy the fat JAR into the container
COPY build/libs/medivault-logserver-0.0.1-SNAPSHOT-all.jar ./logserver.jar

# Expose port (matches your LogServer.java)
EXPOSE 5555

# Environment variable for HMAC key
ENV AUDIT_HMAC_KEY=change_this_secret_key

# Run the log server
ENTRYPOINT ["java", "-jar", "logserver.jar"]

