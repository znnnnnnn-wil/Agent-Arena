FROM maven:3.9.11-eclipse-temurin-17@sha256:e4a7ace3dc0d645ed97f8d9ad0b0d3f0b14fa8d150138f27f116d7105a639b82 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline
COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre@sha256:f9e65324a37f28209ce7dd0e5149a7aa954520ed936fb87813cf6ded2400a112
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/agentArena-0.0.1-SNAPSHOT.jar /app/agent-arena.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/agent-arena.jar"]
