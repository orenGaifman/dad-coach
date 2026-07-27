# Stage 1: Build with Maven wrapper
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper and project definition first for better layer caching
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

# Make mvnw executable (in case permissions aren't preserved)
RUN chmod +x mvnw

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime with minimal JRE image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
