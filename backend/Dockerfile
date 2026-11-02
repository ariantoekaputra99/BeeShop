FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S bee && adduser -S bee -G bee && mkdir -p /app/data/images && chown -R bee:bee /app/data
COPY --from=build /src/target/beeshop-backend-*.jar app.jar
ENV IMAGE_DIR=/app/data/images
VOLUME ["/app/data"]
USER bee
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
