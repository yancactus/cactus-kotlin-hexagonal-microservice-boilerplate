# Build stage
FROM maven:3.9-eclipse-temurin-23-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:23-jre-alpine
WORKDIR /app

RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -D appuser

COPY --from=build /app/target/*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
