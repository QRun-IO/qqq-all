# Core reference app

Build with Java 21 and Maven, then start the standalone core profile:

```sh
mvn -pl qqq-all-app -am verify
java -jar qqq-all-app/target/qqq-all-app.jar
```

The core app binds to `127.0.0.1` and serves the Next dashboard at `http://127.0.0.1:8080/` and health at `/health`. Set `QQQ_ALL_PORT` to change the port and `QQQ_ALL_DATA_DIR` to change the default `./data` directory. On first start it seeds customers and orders in H2, order lines in SQLite, and products as JSON files. Artemis runs in the same JVM; inserts and updates to `order` publish to the `orderEvents` topic and trigger `syncOrder`.

`QQQ_ALL_BIND_HOST` can select another interface when intentionally running behind a container port mapping. The Compose core service sets it to `0.0.0.0` inside the container while publishing only on host loopback.

Core uses mock authentication. The `sessionId` cookie selects the two fixed demo identities: `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa` for Admin and `dddddddd-dddd-4ddd-8ddd-dddddddddddd` for Demo User. Any other session ID uses Demo User. Admin can read and write the customer, order, order-line, product, and address tables; Demo User can read them but cannot write them. These identities are for local demonstrations. Quick search is disabled in core because it requires OpenSearch; the customer list remains available through the normal table query API.

The core demo seeds a customer table view, a shipping address linked to the geo country/state/city tables, and an order review workflow. The `RunRecordWorkflow` process can run that workflow against an order; `syncOrder` writes a process trace after an order event. It also seeds an active `orderStored` webhook subscription to its receiver at `/demo/order-webhook`. Insert an order as Admin, run `SendWebhookEvent` as Admin, and inspect `/demo/order-webhook-receipts` to see the delivered payload. Demo User can read webhook records but cannot edit their configuration or invoke the delivery process. The receiver is available only in the core profile, which Compose publishes on host loopback.

## Full profile

Set `QQQ_ALL_PROFILE=full` to use external services. The full profile requires an explicit `QQQ_ALL_BIND_HOST`; use `127.0.0.1` for local-only access or deliberately set another interface when a reverse proxy needs it. `QQQ_ALL_PORT` defaults to `8080`. The full profile replaces core's mock authentication with Keycloak OIDC. It does not start an embedded broker.

Configure the following environment variables before starting the same jar:

| Service | Required variables | Optional variables (default) |
| --- | --- | --- |
| PostgreSQL | `QQQ_ALL_POSTGRES_HOST`, `QQQ_ALL_POSTGRES_DATABASE`, `QQQ_ALL_POSTGRES_USER`, `QQQ_ALL_POSTGRES_PASSWORD` | `QQQ_ALL_POSTGRES_PORT` (5432) |
| MySQL | `QQQ_ALL_MYSQL_HOST`, `QQQ_ALL_MYSQL_DATABASE`, `QQQ_ALL_MYSQL_USER`, `QQQ_ALL_MYSQL_PASSWORD` | `QQQ_ALL_MYSQL_PORT` (3306) |
| MongoDB replica set | `QQQ_ALL_MONGO_HOST`, `QQQ_ALL_MONGO_DATABASE`, `QQQ_ALL_MONGO_USER`, `QQQ_ALL_MONGO_PASSWORD`, `QQQ_ALL_MONGO_REPLICA_SET` | `QQQ_ALL_MONGO_PORT` (27017), `QQQ_ALL_MONGO_AUTH_DATABASE` (admin) |
| MinIO S3 | `QQQ_ALL_S3_ENDPOINT`, `QQQ_ALL_S3_BUCKET`, `QQQ_ALL_S3_ACCESS_KEY`, `QQQ_ALL_S3_SECRET_KEY` | `QQQ_ALL_S3_REGION` (us-east-1) |
| SFTP | `QQQ_ALL_SFTP_HOST`, `QQQ_ALL_SFTP_USER`, `QQQ_ALL_SFTP_PASSWORD` | `QQQ_ALL_SFTP_PORT` (22), `QQQ_ALL_SFTP_BASE_PATH` (/upload) |
| ESB | `QQQ_ALL_ARTEMIS_URL`, `QQQ_ALL_ARTEMIS_USER`, `QQQ_ALL_ARTEMIS_PASSWORD`, `QQQ_ALL_ARTEMIS_MANAGEMENT_URL`, `QQQ_ALL_RABBITMQ_URL`, `QQQ_ALL_RABBITMQ_USER`, `QQQ_ALL_RABBITMQ_PASSWORD`, `QQQ_ALL_RABBITMQ_MANAGEMENT_URL` | — |
| Mailpit SMTP | `QQQ_ALL_SMTP_HOST` | `QQQ_ALL_SMTP_PORT` (1025) |
| Keycloak OIDC | `QQQ_ALL_OIDC_BASE_URL`, `QQQ_ALL_OIDC_CLIENT_ID`, `QQQ_ALL_OIDC_CLIENT_SECRET` | `QQQ_ALL_OIDC_EXTERNAL_BASE_URL` (base URL), `QQQ_ALL_OIDC_SCOPES` (`openid email profile`) |
| OpenSearch quick search | `QQQ_ALL_OPENSEARCH_HOST` | `QQQ_ALL_OPENSEARCH_PORT` (9200), `QQQ_ALL_OPENSEARCH_INDEX` (`qqq-all-customers`), `QQQ_ALL_OPENSEARCH_USER`, `QQQ_ALL_OPENSEARCH_PASSWORD`, `QQQ_ALL_OPENSEARCH_SSL` (false) |

The full app adds sample tables on each external backend, `orderEvents` on Artemis, and `orderSyncEvents` on RabbitMQ. Quick search is configured for customer `name` and `email`; run `quickSearchFullReindex` after OpenSearch and the customer data are ready. The SFTP integration adds its import source and staging tables plus `SFTPImportFileSyncProcess`; `externalImportFile` remains the direct SFTP file sample. Keycloak realm roles resolve by name against the PostgreSQL `role` table from `qbit-user-role-permissions`, then become QQQ session permissions. Unknown roles receive no permissions. Webhook tables and delivery processes require explicit permissions: the viewer role receives read access, while the admin role can edit destinations and run delivery. The Compose profile provisions demo SQL tables, OAuth session and redirect-state tables, qbit permission tables and roles, and the MinIO bucket on first startup; `infra/postgres/03-webhook-permissions.sql` adds webhook grants after the role seed.

After starting the full Compose profile, run the opt-in broker management smoke test with the same broker credentials. For the supplied demo environment:

```sh
set -a
. ./.env.example
set +a
QQQ_ALL_COMPOSE_BROKERS=true mvn -pl qqq-all-app -am -Dtest=FullProfileBrokerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The test uses the loopback management ports to read and pause/resume the Artemis subscription queue, then creates, reads, purges, and deletes a temporary RabbitMQ queue. For a customized `.env`, source that file instead. It is skipped during ordinary `mvn verify`.
