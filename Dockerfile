FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline
COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/agentArena-0.0.1-SNAPSHOT.jar /app/agent-arena.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/agent-arena.jar"]
