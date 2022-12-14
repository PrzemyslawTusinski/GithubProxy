FROM eclipse-temurin:11-jdk-jammy

VOLUME /tmp

COPY target/Github-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
