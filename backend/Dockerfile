# ---- build stage: full Maven + JDK, thrown away after the jar is built ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline          # cached layer: only re-runs when pom.xml changes
COPY src ./src
RUN mvn -B -q package -DskipTests            # tests run in CI, not in the image build

# ---- runtime stage: slim JRE only, non-root ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/tinytrail-*.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-Xmx512m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
