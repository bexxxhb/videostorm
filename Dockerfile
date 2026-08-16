# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve in their own layer so a source-only change does not re-download them.
COPY pom.xml ./
RUN mvn -B -Dmaven.repo.local=/root/.m2/repository dependency:go-offline

COPY src ./src
RUN mvn -B -Dmaven.repo.local=/root/.m2/repository -DskipTests package \
    && mv target/videostorm-*.jar target/videostorm.jar


FROM eclipse-temurin:21-jre-alpine

# tzdata backs the container's system clock/logs with Europe/Berlin; the JVM itself resolves
# ZoneId.of("Europe/Berlin") from its own bundled tz database regardless.
RUN apk add --no-cache tzdata
ENV TZ=Europe/Berlin

# The application never writes to the filesystem and must never run as root.
RUN addgroup -S -g 1000 videostorm \
    && adduser -S -u 1000 -G videostorm -H -s /sbin/nologin videostorm

WORKDIR /app
COPY --from=build --chown=videostorm:videostorm /build/target/videostorm.jar ./videostorm.jar

USER videostorm:videostorm
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/videostorm.jar"]
