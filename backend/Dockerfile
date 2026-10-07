# The Dad Coach backend. Render's dad-coach service builds this file with root directory backend/ (build
# context = backend/); docker-compose uses the same file. Stage 1 builds with the Maven wrapper, stage 2 runs on a JRE.
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Maven wrapper and project definition first, for layer caching
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -Dmaven.test.skip=true -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar
EXPOSE 8080

# Small instance: serial GC, 75% of the container's memory, lazy bean creation for a fast start
ENTRYPOINT ["java", \
    "-XX:+UseSerialGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Dspring.main.lazy-initialization=true", \
    "-jar", "/app/app.jar"]
