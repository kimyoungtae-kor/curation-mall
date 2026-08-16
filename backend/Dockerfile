FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package


FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S spring \
    && adduser -S spring -G spring

WORKDIR /app

COPY --from=build --chown=spring:spring /workspace/target/backend-*.jar /app/app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
