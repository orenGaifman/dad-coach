# Stage 1: Build with Maven wrapper
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper and project definition first for better layer caching
COPY backend/.mvn .mvn
COPY backend/mvnw .
COPY backend/pom.xml .

# Make mvnw executable (in case permissions aren't preserved)
RUN chmod +x mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build
COPY backend/src src
RUN ./mvnw clean package -Dmaven.test.skip=true -B

# Stage 2: Runtime with minimal JRE image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080

# JVM options for faster startup on constrained resources
ENTRYPOINT ["java", \
    "-XX:+UseSerialGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Dspring.main.lazy-initialization=true", \
    "-jar", "/app/app.jar"]
