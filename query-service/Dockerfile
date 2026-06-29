# Multi-stage build. Context = repo root:  docker build -f query-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs ./libs
COPY query-service ./query-service
RUN mvn -q -B -N -DskipTests install \
 && mvn -q -B -f libs/documind-contracts/pom.xml -DskipTests install \
 && mvn -q -B -f libs/documind-common/pom.xml -DskipTests install \
 && mvn -q -B -f query-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/query-service/target/query-service-1.0.0.jar app.jar
EXPOSE 8002
ENTRYPOINT ["java", "-jar", "app.jar"]
