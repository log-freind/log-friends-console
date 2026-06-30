FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY src src

RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon
RUN cp build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
