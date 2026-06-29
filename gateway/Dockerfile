# Multi-stage build. Context = repo root:  docker build -f gateway/Dockerfile .
# gateway depends only on documind-contracts.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs ./libs
COPY gateway ./gateway
RUN mvn -q -B -N -DskipTests install \
 && mvn -q -B -f libs/documind-contracts/pom.xml -DskipTests install \
 && mvn -q -B -f gateway/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/gateway/target/gateway-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
