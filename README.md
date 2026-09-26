# qqq-all

QQQ with every feature configured and working out of the box: every storage backend, quick search, the ESB (ActiveMQ Artemis and RabbitMQ), OIDC, and the Next dashboard. It runs in two profiles: `core` (no containers, `java -jar`) and `full` (`docker compose --profile full up`).

**Status:** under construction. qqq-all versions track QQQ and build on `4.1.0-SNAPSHOT` until QQQ 4.1 ships. See the [design](docs/design.md), the [plan](docs/plan.md), and the epic [#1](https://github.com/QRun-IO/qqq-all/issues/1). It is a local demo and reference build, not a production deployment.

## Modules

| Module | Purpose |
|---|---|
| `qqq-all-bom` | One tested version set for QQQ, the ESB, the Next dashboard, the included qBits, JDBC drivers, and broker clients |
| `qqq-all-app` | The reference application with every backend, qBit, and the ESB wired, plus seeded demo data |

## Build

Requires Java 21 and Maven.

```bash
mvn -B verify
```

## Run locally

The core profile needs only Java 21:

```bash
java -jar qqq-all-app/target/qqq-all-app.jar
```

For containers, copy the demo settings and build the jar before starting either Compose profile:

```bash
cp .env.example .env
mvn -B verify
docker compose --profile core up --build --wait
# Or: docker compose --profile full up --build --wait
```

The app is at `http://127.0.0.1:8080/health` (change `QQQ_ALL_PORT` in `.env` if needed). The full profile also publishes local-only management ports for Keycloak (`8081`), Mailpit (`8025`), MinIO (`9001`), Artemis/Jolokia (`8161`), and RabbitMQ (`15672`). The demo Keycloak users are `demo-admin` and `demo-user`; their passwords and the confidential client secret come from `.env`. The sample values in `.env.example` are for local demonstrations only. The default browser-visible issuer is `http://keycloak.localhost:8081`; if your host does not resolve `keycloak.localhost` to loopback, add that local hosts entry before trying OIDC login.

The full stack initializes the MongoDB replica set, MinIO bucket, sample SQL tables, OAuth2 session tables, and demo roles/permissions. PostgreSQL and MySQL initialization scripts run only when their data volumes are first created. To stop, run `docker compose --profile full down`; adding `--volumes` removes demo data. See [the app guide](qqq-all-app/README.md) for the full-profile environment contract.

Artemis requires a configured login on a fresh volume. If an older demo volume was created with anonymous login enabled, recreate that volume before relying on the new setting; `docker compose --profile full down --volumes` resets all local demo data.

On merges to `develop`, [the image workflow](.github/workflows/publish-image.yml) checks that the `qqq-all` container package is already public, verifies the reactor, and pushes `ghcr.io/qrun-io/qqq-all:develop`. GitHub creates a new container package private by default, so the first image requires a deliberate, one-time bootstrap: an organization owner publishes an initial image, then changes the package visibility to **public** in GitHub package settings. Until that is done, the workflow fails before any image push. Do not enable automatic develop publication until the package is public.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md), the [Code of Conduct](CODE_OF_CONDUCT.md), and the [security policy](SECURITY.md).

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
