# Build stage. Tests are not run here: they run in CI with "./mvnw verify".
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies in their own layer, reused as long as pom.xml does not change.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp package -DskipTests

# Runtime stage: JRE only, running as a non-root user.
FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/target/github-proxy.jar /app/app.jar
USER app

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
