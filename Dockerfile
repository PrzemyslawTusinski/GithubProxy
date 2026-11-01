# Build stage. Tests are skipped here to keep the image build fast; run ./mvnw verify before building an image.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# The Maven wrapper pins the Maven version in one place. Dependencies are resolved in their own layer,
# reused as long as pom.xml and the wrapper do not change.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp package -DskipTests

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
