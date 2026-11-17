FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
# Run as an unprivileged user; /app/uploads is only used when STORAGE_TYPE=local.
RUN useradd --system --uid 10001 --home /app techy && mkdir -p /app/uploads && chown -R techy /app
COPY --from=build --chown=techy /app/target/dev-social-0.1.0.jar app.jar
USER techy
# Size the heap from the container's memory limit (small cloud instances often have 512 MB).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# The app listens on $PORT when the host sets it (Render, Fly, Railway...), otherwise 8080.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
