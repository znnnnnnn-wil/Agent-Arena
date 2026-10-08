FROM maven:3.9-eclipse-temurin-26@sha256:b2c1ad85954592f9928e84327c65201f308ad9b5d8ed7d5b823717c97bf23fbb AS build
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
