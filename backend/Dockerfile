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
# Tuned for small cloud instances (e.g. Render free: 512 MB, 0.1 CPU), measured there-like:
#  - heap sized from the container limit, leaving room for non-heap memory
#  - Serial GC and the quick (C1-only) JIT: on a fraction of a CPU this halves startup
#    (about 236 s -> 111 s) and lowers memory (about 300 -> 250 MB)
# Override JAVA_TOOL_OPTIONS on the host if you run on a bigger machine.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"
# The app listens on $PORT when the host sets it (Render, Fly, Railway...), otherwise 8080.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
