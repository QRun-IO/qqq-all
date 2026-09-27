# Core reference app

Build with Java 21 and Maven, then start the standalone core profile:

```sh
mvn -pl qqq-all-app -am verify
java -jar qqq-all-app/target/qqq-all-app.jar
```

The core app binds to `127.0.0.1` and serves the Next dashboard at `http://127.0.0.1:8080/` and health at `/health`. Set `QQQ_ALL_PORT` to change the port and `QQQ_ALL_DATA_DIR` to change the default `./data` directory. On first start it seeds customers and orders in H2, order lines in SQLite, and products as JSON files. Artemis runs in the same JVM; inserts and updates to `order` publish to the `orderEvents` topic and trigger `syncOrder`.

`QQQ_ALL_BIND_HOST` can select another interface when intentionally running behind a container port mapping. The Compose core service sets it to `0.0.0.0` inside the container while publishing only on host loopback.

Core uses mock authentication. The `sessionId` cookie selects the two fixed demo identities: `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa` for Admin and `dddddddd-dddd-4ddd-8ddd-dddddddddddd` for Demo User. Any other session ID uses Demo User. Admin can read and write the customer, order, order-line, product, and address tables and open the ESB overview at `/qqq/v1/esb/overview`; Demo User can read those tables but cannot write them or open the ESB overview. These identities are for local demonstrations. Quick search is disabled in core because it requires OpenSearch; the customer list remains available through the normal table query API.

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

The full-profile admin receives ESB service visibility, queue controls, and message deletion through `infra/postgres/04-esb-permissions.sql`; the viewer role does not. The full smoke checks the RabbitMQ process-completion event through QQQ's authenticated ESB message endpoint, which decodes the broker's JMS message. Fresh PostgreSQL volumes apply this SQL automatically. For a running full-profile stack with an existing volume, apply the idempotent grant file once:

```bash
COMPOSE_PROFILES=full docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < infra/postgres/04-esb-permissions.sql
```

Then sign in as Admin and open the ESB overview. The Viewer role must remain unable to open it.

On startup, the full demo creates its remaining PostgreSQL qbit tables from the registered metadata and checks every expected column and type. The hand-maintained domain, OAuth, and role tables stay under `infra/postgres`. If a persisted qbit table is missing a required column or has a different type, startup fails with a migration/reset message instead of silently accepting it. The full-profile RabbitMQ completion-event smoke requires QQQ's process publishing and permission-scoped ESB message endpoint; the broker management smoke below can run independently.

After starting the full Compose profile, run the opt-in broker management smoke test with the same broker credentials. For the supplied demo environment:

```sh
set -a
. ./.env.example
set +a
QQQ_ALL_COMPOSE_BROKERS=true mvn -pl qqq-all-app -am -Dtest=FullProfileBrokerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The test uses the loopback management ports to read and pause/resume the Artemis subscription queue, then creates, reads, purges, and deletes a temporary RabbitMQ queue. For a customized `.env`, source that file instead. It is skipped during ordinary `mvn verify`.

## Local runtime demonstrations (#21)

Both profiles include the BOM-managed API backend and JavaScript module. The shaded
JAR retains Nashorn's `ScriptEngineFactory` service registration. No extra service,
API key, script editor, or user-supplied code is needed.

| Demo | Behavior | Access |
| --- | --- | --- |
| `apiCatalog` | QQQ API-backed table reads three synthetic products from this app's `/demo/catalog` HTTP endpoint; native queries page through the endpoint and get-by-ID uses `/demo/catalog/{id}`. | Admin and Demo/Viewer can read; neither receives write grants. |
| `calculateOrderTotal` | Executes fixed JavaScript through `ExecuteCodeAction`: quantity × unit price in cents → `totalCents`. It changes no records. | Admin only. |
| `demoNote` | Memory-backed table, seeded with `Transient demo note` on every start and cleared on shutdown. | Admin can edit; Demo/Viewer can read. |

The internal catalog URL follows `QQQ_ALL_BIND_HOST` and the configured HTTP port.
IPv4/IPv6 wildcard listeners use their corresponding loopback address; literal
IPv6 hosts are bracketed correctly in the URL.

The catalog endpoint exposes **only synthetic public demo data**. It is not an
outbound proxy and does not forward session credentials. The adapter supports
ascending ID order, limit/skip pagination, and one exact ID filter; unsupported
filters and sorts fail explicitly. Native get of an absent ID returns no record.
The endpoint rejects invalid or excessive page sizes (maximum 1000).

With the core JAR running on port 8080, exercise the real QQQ routes:

```sh
curl -fsS -H 'Content-Type: application/json' -d '{}' http://127.0.0.1:8080/qqq/v1/table/apiCatalog/query
curl -fsS http://127.0.0.1:8080/data/apiCatalog/103
curl -fsS -H 'Cookie: sessionId=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' -H 'Content-Type: application/json' -d '{}' 'http://127.0.0.1:8080/processes/calculateOrderTotal/run?quantity=3&unitPriceCents=1250'
```

The last response contains `values.totalCents: 3750`. Quantity must be an integer
in 1..1000; unit price must be an integer in 0..1000000 cents. The process does not
accept code from the caller. This demonstration does not alter stored-script or
Test-mode behavior.

Memory notes are deliberately **not persisted** with the data directory or a
Compose volume. Restarting discards edits and restores the seed. Like the existing
QQQ memory and ESB runtime singletons, this demo assumes one application per JVM.
The memory lifecycle touches only `demoNote`; it never resets the global store.

Full-profile PostgreSQL now uses QQQ's first-party `PostgreSQLBackendMetaData` and
strategy, including driver/URL selection, quoted identifiers, default-value
inserts, generated IDs, null binding, and UTC timestamp handling. Existing qbit
schema provisioning and compatibility checks remain in place.

Fresh full-profile volumes apply `infra/postgres/05-runtime-demo-permissions.sql`.
For an existing volume, apply the idempotent grants and then sign in again:

```sh
COMPOSE_PROFILES=full docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < infra/postgres/05-runtime-demo-permissions.sql
```

`mvn -pl qqq-all-app -am verify` includes native local API, calculation/permission,
memory restart, and packaged-JAR checks. `scripts/run_core_smoke.py` and
`scripts/run_full_smoke.py` verify these routes with actual Admin and restricted
sessions; full smoke also retains the OIDC, qbit, and broker checks. The opt-in
`PostgresCompatibilityTest` uses `QQQ_ALL_TEST_POSTGRES_PORT` and
`QQQ_ALL_TEST_POSTGRES_PASSWORD` against a local `qqq` database/user; it creates and
drops only a uniquely named test table. It is skipped during ordinary verify.
All eight included qbits, their explicit deferrals, and release gates are unchanged.
