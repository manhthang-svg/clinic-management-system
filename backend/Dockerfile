# Backend: Java 21, Spring Boot. Build stage packages the jar, runtime stage only runs it.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first so they stay cached while only the source changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY lombok.config ./
COPY src src
# Tests run in CI (./mvnw verify); the image only packages the application.
RUN ./mvnw -B -q package -DskipTests \
    && find target -maxdepth 1 -name '*.jar' ! -name '*.original' -exec cp {} app.jar \;


FROM eclipse-temurin:21-jre
ENV TZ=Asia/Ho_Chi_Minh \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
WORKDIR /app

# Runs as a non-root user; /app/data holds the medical-record archive and the signing simulator files.
RUN groupadd --system clinic && useradd --system --gid clinic --home /app clinic \
    && mkdir -p /app/data && chown -R clinic:clinic /app
COPY --from=build --chown=clinic:clinic /workspace/app.jar app.jar
USER clinic

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
