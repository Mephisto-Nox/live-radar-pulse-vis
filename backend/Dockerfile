FROM maven:3.10.0-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -B verify

FROM eclipse-temurin:25.0.5_8-jre
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring --home-dir /app spring
COPY --from=build /workspace/target/pulse-vision-api.jar /app/app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
