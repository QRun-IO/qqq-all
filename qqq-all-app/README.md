# Core reference app

Build with Java 21 and Maven, then start the standalone core profile:

```sh
mvn -pl qqq-all-app -am verify
java -jar qqq-all-app/target/qqq-all-app-4.1.0-SNAPSHOT.jar
```

The core app binds to `127.0.0.1` and serves the Next dashboard at `http://127.0.0.1:8080/` and health at `/health`. Set `QQQ_ALL_PORT` to change the port and `QQQ_ALL_DATA_DIR` to change the default `./data` directory. On first start it seeds customers and orders in H2, order lines in SQLite, and products as JSON files. Artemis runs in the same JVM; inserts and updates to `order` publish to the `orderEvents` topic and trigger `syncOrder`.

Core uses mock authentication. The `sessionId` cookie selects the two fixed demo identities: `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa` for Admin and `dddddddd-dddd-4ddd-8ddd-dddddddddddd` for Demo User. Any other session ID uses Demo User. These identities are for local demonstrations; role-based permissions are wired in task C5.

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
| ESB | `QQQ_ALL_ARTEMIS_URL`, `QQQ_ALL_RABBITMQ_URL`, `QQQ_ALL_RABBITMQ_USER`, `QQQ_ALL_RABBITMQ_PASSWORD` | — |
| Mailpit SMTP | `QQQ_ALL_SMTP_HOST` | `QQQ_ALL_SMTP_PORT` (1025) |
| Keycloak OIDC | `QQQ_ALL_OIDC_BASE_URL`, `QQQ_ALL_OIDC_CLIENT_ID`, `QQQ_ALL_OIDC_CLIENT_SECRET` | `QQQ_ALL_OIDC_EXTERNAL_BASE_URL` (base URL), `QQQ_ALL_OIDC_SCOPES` (`openid email profile`) |

The full app adds sample tables on each external backend, `orderEvents` on Artemis, and `orderSyncEvents` on RabbitMQ. Keycloak realm roles resolve by name against the PostgreSQL `role` table from `qbit-user-role-permissions`, then become QQQ session permissions. Unknown roles receive no permissions. PostgreSQL must also contain the qbit permission tables and OAuth session and redirect-state tables. External database schema, demo roles, the MinIO bucket, and the service stack are provisioned by the forthcoming compose and seed work in C6; this C4 profile expects those resources to exist.
