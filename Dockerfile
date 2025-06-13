# ---- Build stage (optional, if using multi-stage build) ----
# FROM gradle:8.13-jdk21 AS build
# WORKDIR /home/gradle/project
# COPY . .
# RUN gradle build --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install curl and ping utilities
RUN apk add --no-cache curl iputils

# Set the working directory
WORKDIR /app

# Copy the built jar from the build context
COPY build/libs/globeco-order-service-0.0.1-SNAPSHOT.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"] 