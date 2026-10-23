FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src

RUN mvn -B -ntp clean verify

FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

COPY --from=builder --chown=10001:10001 \
    /workspace/target/monolith-0.0.1-SNAPSHOT.jar \
    /app/app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]