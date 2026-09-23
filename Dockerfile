# ---- Build stage: run the Gradle wrapper to produce the Spring Boot fat jar ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# Copy the wrapper and build scripts first so dependency resolution is cached
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

# Copy sources and build the executable jar (tests are run separately in CI/local)
COPY src/ src/
RUN ./gradlew --no-daemon -x test bootJar \
    && JAR=$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -1) \
    && cp "$JAR" /workspace/app.jar

# ---- Runtime stage: minimal JRE image ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --home /app --shell /sbin/nologin appuser
COPY --from=builder /workspace/app.jar /app/app.jar
RUN chown -R appuser:appuser /app

USER appuser
ENV PORT=8081
EXPOSE 8081

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]