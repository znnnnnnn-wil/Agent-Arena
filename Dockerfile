FROM maven:3.9.15-eclipse-temurin-26@sha256:029a8e2838ae68238ffb8be407cddbb3f07d4d839c60c6f26c619a69fd184531 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline
COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:17-jre@sha256:13cc28a6cc72a38ce1f00c906be3580c1a3e604b8984d694f369a96742abc93b
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/agentArena-0.0.1-SNAPSHOT.jar /app/agent-arena.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/agent-arena.jar"]
