# qqq-all

An evolving reference app for QQQ 4.1. The `core` profile runs locally from one jar; `full` adds external backends and services. Compose and the remaining QBit demos are in separate, unmerged work.

**Status:** under construction. qqq-all versions track QQQ and build on `4.1.0-SNAPSHOT` until QQQ 4.1 ships. See the [design](docs/design.md), the [plan](docs/plan.md), and the epic [#1](https://github.com/QRun-IO/qqq-all/issues/1).

## Quick start

From the repository root, install Java 21 and Maven, then build the app and its BOM with `mvn -B verify`. This needs access to the QQQ 4.1 snapshot dependencies described in the [app guide](qqq-all-app/README.md).

**Core:** run `java -jar qqq-all-app/target/qqq-all-app-4.1.0-SNAPSHOT.jar`. Open `http://127.0.0.1:8080/`; `/health` is the health endpoint. The app stores local data under `./data` and merges sample customer and order rows on every start, which can reset edits to those rows. Set `QQQ_ALL_PORT` or `QQQ_ALL_DATA_DIR` to change those defaults. Core uses mock demo identities, so keep it on the loopback address.

**Full:** first provision PostgreSQL, MySQL, a MongoDB replica set, MinIO, SFTP, Artemis, RabbitMQ, Mailpit, Keycloak, and the required schemas and bucket. Set the `QQQ_ALL_*` variables listed in the [app guide](qqq-all-app/README.md). Then run `QQQ_ALL_PROFILE=full QQQ_ALL_BIND_HOST=127.0.0.1 java -jar qqq-all-app/target/qqq-all-app-4.1.0-SNAPSHOT.jar`. Full does not start those services itself; the Compose setup is still in C6. Keep `QQQ_ALL_BIND_HOST` on loopback for a local demo.

## Feature tour

| Feature | Where to find it |
|---|---|
| Next dashboard and health | The root page and `/health` in both profiles. |
| Local storage | Seeded `customer` and `order` tables in H2, `orderLine` in SQLite, and `product` JSON files under `./data/files` in core. |
| Event bus | An `order` insert or update publishes to the embedded Artemis `orderEvents` topic and triggers `syncOrder` in core. |
| External storage | Full adds `warehouseCustomer` (PostgreSQL), `supplierOrder` (MySQL), `shipment` (MongoDB), `document` (MinIO S3), and `importFile` (SFTP). |
| Messaging and email | Full uses external Artemis for `orderEvents`, publishes completed `syncOrder` events to RabbitMQ `orderSyncEvents`, and configures Mailpit SMTP. |
| Authentication and permissions | Core has mock Admin and Demo User identities. Full uses Keycloak OIDC and maps realm roles through `qbit-user-role-permissions`; unknown roles get no permissions. |

The tour reflects code already on `develop`. Quick search and the other QBit examples are being added in C5; the Compose/image quick start is being added in C6.

## Local-demo limits

This is a local demo and reference build, not a production deployment. Core uses mock authentication and a nonpersistent embedded broker. A production installation needs its own DNS, TLS termination, backups and restore testing, monitoring and alerting, and highly available brokers. Review authentication, credentials, data persistence, and network exposure before deploying beyond a local machine.

## Modules

| Module | Purpose |
|---|---|
| `qqq-all-bom` | One tested version set for QQQ, the ESB, the Next dashboard, the included qBits, JDBC drivers, and broker clients |
| `qqq-all-app` | The core reference app and full-profile external-service wiring, plus seeded demo data |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md), the [Code of Conduct](CODE_OF_CONDUCT.md), and the [security policy](SECURITY.md).

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
