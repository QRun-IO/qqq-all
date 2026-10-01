FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 qqq \
    && useradd --system --uid 10001 --gid qqq --home-dir /app qqq \
    && mkdir -p /app/data \
    && chown -R qqq:qqq /app

WORKDIR /app
COPY --chown=qqq:qqq qqq-all-app/target/qqq-all-app.jar /app/qqq-all.jar
COPY --chown=qqq:qqq LICENSE NOTICE /app/
USER 10001:10001
EXPOSE 8080
ENV QQQ_ALL_DATA_DIR=/app/data
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=6 \
    CMD curl --fail --silent http://127.0.0.1:8080/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/qqq-all.jar"]
