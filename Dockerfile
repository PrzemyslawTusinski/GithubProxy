FROM maven:3.8.6-eclipse-temurin-17 AS BUILD
COPY src /home/app/src
COPY pom.xml /home/app

RUN mvn -f /home/app/pom.xml package

FROM eclipse-temurin:17-jdk-jammy AS PACKAGE

COPY --from=build /home/app/target/Github-0.0.1-SNAPSHOT.jar /usr/local/lib/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/usr/local/lib/app.jar"]
