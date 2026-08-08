# ECS uses linux/amd64; digests pin both build and runtime inputs for repeatable rebuilds.
FROM docker.io/library/maven:3.9.12-eclipse-temurin-21-alpine@sha256:9c100585de7b83335cbd2836ef2b5ebcf3e3c93a9e994071019e3c9a8f0dafd0 AS build

ARG BUILD_OUTPUT_EPOCH=0
WORKDIR /workspace

COPY backend/pom.xml ./pom.xml
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY backend/src ./src
RUN mvn --batch-mode --no-transfer-progress \
      -DskipTests \
      -Dproject.build.outputTimestamp="${BUILD_OUTPUT_EPOCH}" \
      package \
    && cp target/rotrack-api-*.jar /workspace/application.jar

FROM docker.io/library/eclipse-temurin:21-jre-alpine@sha256:426401268a42785be73823f6115ee0e721bdb59c12c779947b83fcead1a66645 AS runtime

ARG IMAGE_CREATED
ARG IMAGE_REVISION
ARG IMAGE_VERSION
LABEL org.opencontainers.image.title="rotrack-api" \
      org.opencontainers.image.description="rotrack Spring Boot API" \
      org.opencontainers.image.created="${IMAGE_CREATED}" \
      org.opencontainers.image.revision="${IMAGE_REVISION}" \
      org.opencontainers.image.version="${IMAGE_VERSION}"

RUN addgroup -S -g 10001 rotrack \
    && adduser -S -D -H -u 10001 -G rotrack rotrack \
    && mkdir -p /opt/rotrack/bin \
    && chown -R 10001:10001 /opt/rotrack

COPY --from=build --chown=10001:10001 /workspace/application.jar /opt/rotrack/application.jar
COPY --chown=10001:10001 scripts/container/container-entrypoint.sh /opt/rotrack/bin/container-entrypoint.sh

ENV PORT=8080 \
    SERVER_SHUTDOWN=graceful \
    SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=25s \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Djava.io.tmpdir=/tmp"

USER 10001:10001
EXPOSE 8080
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -T 4 -O /dev/null http://127.0.0.1:8080/api/v1/health || exit 1

ENTRYPOINT ["/opt/rotrack/bin/container-entrypoint.sh"]
