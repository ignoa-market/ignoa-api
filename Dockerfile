FROM eclipse-temurin:21-jre

WORKDIR /app

ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.31.1/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

COPY build/libs/*.jar app.jar

EXPOSE 38080

ENTRYPOINT ["java", "-jar", "app.jar"]
