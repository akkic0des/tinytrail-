# ---- build stage: full Maven + JDK, thrown away after the jar is built ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Retries + timeouts: Maven Central occasionally drops connections from cloud build hosts.
ARG MVN_FLAGS="-B -Dmaven.wagon.http.retryHandler.count=3 -Daether.connector.http.retryHandler.count=3 -Daether.connector.connectTimeout=30000 -Daether.connector.requestTimeout=120000"
COPY pom.xml .
COPY src ./src
# No -q so failures are readable; one whole-build retry covers transient download errors.
RUN mvn $MVN_FLAGS package -DskipTests || (sleep 15 && mvn $MVN_FLAGS package -DskipTests)

# ---- runtime stage: slim JRE only, non-root ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/tinytrail-*.jar app.jar
USER app
# Heap is set by the host via JAVA_TOOL_OPTIONS (e.g. -Xmx300m on Render's 512 MB free tier).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
