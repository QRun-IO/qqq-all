# qqq-all

A runnable reference app for QQQ 4.1 with two profiles: `core` uses local storage and embedded Artemis; `full` adds external storage, RabbitMQ, OpenSearch quick search, Keycloak, Mailpit, and the included qbits. It is a local demo under construction while QQQ 4.1 finishes. See the [design](docs/design.md), [plan](docs/plan.md), and [epic #1](https://github.com/QRun-IO/qqq-all/issues/1).

## Quick start

Install Java 21 and Maven, then build the app and its BOM from the repository root:

```bash
mvn -B verify
```

Run core from the packaged jar:

```bash
java -jar qqq-all-app/target/qqq-all-app.jar
```

Or copy the local demo settings and run core with Compose:

```bash
cp .env.example .env
docker compose --profile core up --build --wait
```

To run full instead, stop core first, then use the same `.env` (or copy `.env.example` if starting with full):

```bash
docker compose --profile full up --build --wait
```

Open `http://127.0.0.1:8080/` for the dashboard and `/health` for status. The full profile starts PostgreSQL, MySQL, MongoDB, MinIO, SFTP, OpenSearch, Artemis, RabbitMQ, Mailpit, and Keycloak. Its demo logins are `demo-admin` and `demo-user`; their sample passwords and the OIDC client secret come from your local `.env` (copied from [.env.example](.env.example)). The browser issuer is `http://keycloak.localhost:8081`; make that name resolve to loopback if your host does not already. Compose publishes demo ports on host loopback. The [app guide](qqq-all-app/README.md) lists the environment contract and broker-management check.

## Explore the app

Core seeds customers and orders in H2, order lines in SQLite, and product files under `./data`. Insert an order as Admin to publish an Artemis `orderEvents` message and trigger `syncOrder`; inspect its process trace. Run `SendWebhookEvent` as Admin, then inspect the demo webhook receipt. Core's mock Demo User can read but cannot edit webhook destinations or invoke delivery. Sample customer and order rows are merged on every start, so edits to those rows can be reset.

Full adds sample tables on PostgreSQL, MySQL, MongoDB, MinIO S3, and SFTP. It includes OpenSearch customer quick search, table views, process tracing, workflows, geo addresses, SFTP import, and webhook administration with Keycloak role permissions. The `syncOrder` completion publication to RabbitMQ is configured in metadata and depends on QQQ ESB [Task 9](https://github.com/QRun-IO/qqq/pull/786) landing in QQQ 4.1; the app does not publish it through a separate workaround.

To stop the full stack, run `docker compose --profile full down`. Add `--volumes` only when you intend to erase demo data. For a fresh Artemis volume, the configured login is required; old volumes created for anonymous access should be recreated. This reference stack uses demo credentials and is not a production deployment.

The [develop image workflow](.github/workflows/publish-image.yml) verifies the build and publishes to GHCR only after an organization owner bootstraps the container package and sets its visibility to public. Until then, image publication is skipped and local Compose remains available.

The [4.1.0 release procedure](docs/release.md) describes the separate Maven parent/BOM publication path and the manually gated, exact-commit [release workflow](.github/workflows/release.yml). The workflow will not release the current snapshot build.

## Contributing and license

See [CONTRIBUTING.md](CONTRIBUTING.md), the [Code of Conduct](CODE_OF_CONDUCT.md), and the [security policy](SECURITY.md). Licensed under Apache-2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE).
