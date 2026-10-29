FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src

RUN mvn -B -ntp clean verify

FROM eclipse-temurin:25-jre AS runtime

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder --chown=10001:10001 \
    /workspace/target/*.jar /app/app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]