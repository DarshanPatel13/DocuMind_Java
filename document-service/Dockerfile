# Multi-stage build. Context = repo root:  docker build -f document-service/Dockerfile .
# Builds only what this service needs (parent POM + the two libs + the service),
# so the image doesn't require the sibling service modules to be present.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs ./libs
COPY document-service ./document-service
RUN mvn -q -B -N -DskipTests install \
 && mvn -q -B -f libs/documind-contracts/pom.xml -DskipTests install \
 && mvn -q -B -f libs/documind-common/pom.xml -DskipTests install \
 && mvn -q -B -f document-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/document-service/target/document-service-1.0.0.jar app.jar
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "app.jar"]
