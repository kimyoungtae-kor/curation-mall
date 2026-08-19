FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package


FROM eclipse-temurin:21-jre-alpine AS runtime

# Keep the runtime identity stable so a host-persistent media bind mount can be
# provisioned without running the application as root or recursively changing
# ownership on every deployment.
RUN addgroup -S -g 10001 spring \
    && adduser -S -D -H -u 10001 -G spring spring

WORKDIR /app

COPY --from=build --chown=spring:spring /workspace/target/backend-*.jar /app/app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.awt.headless=true", "-XX:MaxRAMPercentage=70.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
