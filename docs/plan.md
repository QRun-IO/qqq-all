# qqq-all Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Each task is one GitHub issue and one PR. Work test-first; list the commands you ran and their results in the PR.

**Goal:** A public repo that runs QQQ with every feature configured and working out of the box, published once qqq 4.1 ships with the ESB and the Next dashboard as default.

**Design:** `docs/design.md` (approved 2026-09-25).

**Depends on:** ESB epic QRun-IO/qqq#739 and Next-as-default QRun-IO/qqq#649, both in qqq 4.1.

**Licensing:** do not change LICENSE files or source headers in any repo. Header/license alignment (several repos still carry AGPL headers and LICENSE files) is a separate owner decision.

## Global Constraints

- Java 21. qqq `4.1.0-SNAPSHOT` until 4.1 GA, then `4.1.x`. qqq-all version tracks qqq.
- Repos use gitflow: branch from and PR to `develop` (qqq-frontend-next: `main`). Never force-push; never delete branches you didn't create.
- Maven: never `mvn install` while other agents build; use `-pl … -am verify`.
- QQQ code style and checkstyle of the target repo; license header copied from a neighboring file.
- No secrets in the repo. Demo credentials live only in `compose.yaml` and the Keycloak realm file, are marked demo-only, and are overridable by env.
- Releases, tags, and Maven Central or GHCR publishing of release versions: ask James first.

## Tasks

### Core prerequisites (QRun-IO/qqq, ship in 4.1)

**A1. Application launcher.** One entry point that starts everything configured.
- `qqq-backend-core`: `QRuntimeServiceInterface { String getName(); void start(QInstance); void stop(); }`, `QInstance.withRuntimeService(QCodeReference)` / `getRuntimeServices()`.
- `qqq-middleware-javalin`: `QApplicationLauncher.run(AbstractQQQApplication, QApplicationLauncherConfig)`. It creates `QApplicationJavalinServer` for the app and reuses the instance that server builds (never a second instance), then starts:
  - `QApplicationJavalinServer`;
  - `QScheduleManager` when the instance has schedules or scheduled jobs;
  - each registered runtime service.
  A JVM shutdown hook stops them in reverse order.
- `qqq-esb`: `EsbInstanceMetaData.enrich` registers `QEsbRuntime` as a runtime service. This part waits for ESB task 10 (QRun-IO/qqq#748).
- Tests: start order, reverse stop order, a service failing to start stops the ones already started and fails the launch, and the scheduler is skipped when nothing is scheduled.

**A2. Fail-fast metadata producers.** With `QInstance.withFailOnMetaDataProducerError(true)` (or system property `qqq.metaData.failOnProducerError=true`), `MetaDataProducerHelper` throws wherever it currently logs a warning and drops a producer (evaluating a candidate producer class during discovery, and executing a producer). The default is unchanged. Tests cover both modes.

### Qbit re-pins (one per repo)

**B1–B8.** Repos: `qbit-quick-search`, `qbit-user-role-permissions`, `qbit-customizable-table-views`, `qbit-standard-process-trace`, `qbit-webhooks`, `qbit-workflows`, `qbit-geo-data`, `qbit-sftp-data-integration`.

Each:
1. If `main` has commits not in `develop`, open a back-merge PR (`main` → `develop`) first. Resolve conflicts keeping develop's features and main's fixes; no force-push.
2. Parent → `com.kingsrook:qbit-build-parent:2.0.0` (imports `qqq-bom-pom` 4.0.0). Remove child `qqq-bom-pom` imports that shadow the parent. Java 21. Leave LICENSE files and headers as they are.
3. Build and test green on the parent's qqq 4.0.0. Also add an opt-in Maven profile `qqq-snapshot` that imports `qqq-bom-pom:${qqq.snapshot.version}` (default `4.1.0-SNAPSHOT`) ahead of the parent's BOM and adds the Central snapshots repository (`https://central.sonatype.com/repository/maven-snapshots/`); `-Pqqq-snapshot` must also build and test green. (The parent has no qqq-version property to override.) The move to a 4.1 parent happens at 4.1 GA (task C9).
4. Integration tests that need Docker fail, not skip, when `CI=true`.

**B1 extra (quick-search):**
- Fix index drift: update and delete re-index or remove documents, and add a reconcile process that rebuilds the index from the source table.
- Remove the stray root `com/` directory and `.claude/settings.local.json`, and add `.claude/settings.local.json` to `.gitignore`.

### qqq-all repo (QRun-IO/qqq-all)

**C1. Bootstrap.**
- README, NOTICE, CODE_OF_CONDUCT, CONTRIBUTING, SECURITY (adapted from qqq), `.github` issue and PR templates, `.gitignore`.
- CircleCI using `qqq-orb`: build plus unit tests on PRs.
- Maven reactor: parent `pom.xml` with modules `qqq-all-bom` and `qqq-all-app`.

**C2. Stack BOM (`qqq-all-bom`).**
- Imports `qqq-bom-pom`.
- Manages: `qqq-esb`, `qqq-frontend-next`, the 8 qbits, JDBC drivers (PostgreSQL, MySQL, SQLite, H2), the ActiveMQ Artemis Jakarta client, and `rabbitmq-jms` 3.9.0.
- A test resolves every managed artifact.

**C3. Reference app, `core` profile (`qqq-all-app`).**
- `QqqAllApplication` starts through `QApplicationLauncher` (A1) with fail-fast producers on (A2).
- Demo domain: customers, orders, order lines, products, with seed data. Backends: H2, SQLite, and the local filesystem. Embedded Artemis for the ESB. Mock auth with admin and demo users.
- ESB: `order` publishes to topic `orderEvents`, and process `syncOrder` is triggered from it.
- Serves the Next dashboard.
- Tests: boots with `java -jar`, health returns 200, every table queries, and the ESB round trip works.

**C4. `full` profile wiring.**
- Env-driven backends: PostgreSQL, MySQL, MongoDB (replica set), S3 (MinIO), SFTP.
- ESB on both Artemis and RabbitMQ (one destination each).
- SMTP to Mailpit.
- OIDC through Keycloak (the OAuth2 module), with roles mapped to permissions via `qbit-user-role-permissions`.
- Profile selected by `QQQ_ALL_PROFILE=core|full`.

**C5. Qbits wired.** All 8 qbits are wired into the app and used by the demo:
- quick search on customers (OpenSearch in `full`, disabled with a notice in `core`);
- a table view, a process trace, a webhook, a workflow, geo data on addresses, and an SFTP import.

**C6. Compose and image.**
- `compose.yaml` with profiles `core` and `full`, healthchecks, and `.env.example`.
- Keycloak realm JSON (demo admin and user). Mongo replica-set init. MinIO bucket init. SFTP (atmoz/sftp). OpenSearch single-node. Artemis with Jolokia. RabbitMQ management.
- `Dockerfile`: Java 21 JRE, non-root user, healthcheck.
- CI publishes `ghcr.io/qrun-io/qqq-all:develop` (public) on develop merges.

**C7. CI smoke and conformance.**
- CI boots `core` via `java -jar` and `full` via `docker compose --profile full up -d --wait`.
- Assertions: health 200; the Next UI is served; each backend's table queries; quick search finds a seeded customer; the ESB round trip works on both brokers; OIDC login works for the demo users.
- CI also runs the ESB broker conformance suite against the compose brokers.

**C8. Docs.**
- README quick start (one command per profile) and a feature tour mapping each QQQ feature to where the demo shows it.
- Limits: local demo and reference only; production needs DNS, TLS, backups, monitoring, HA brokers.

**C9. Publish (after qqq 4.1 GA with ESB and Next default; ask James first).**
- Release a `qbit-build-parent` that imports the 4.1 BOM, re-pin the 8 qbits to it, and release them.
- Pin qqq 4.1.x.
- Tag `4.1.0`.
- Publish the image and `qqq-all-bom` release.
- GitHub release with `compose.yaml`.

## Issues

Epic: QRun-IO/qqq-all#1.

| Task | Issue | Task | Issue |
|---|---|---|---|
| A1 | QRun-IO/qqq#762 | C1 | QRun-IO/qqq-all#2 |
| A2 | QRun-IO/qqq#763 | C2 | #3 |
| B1 quick-search | QRun-IO/qbit-quick-search#6 | C3 | #4 |
| B2 user-role-permissions | QRun-IO/qqq#764 | C4 | #5 |
| B3 customizable-table-views | #765 (qqq) | C5 | #6 |
| B4 standard-process-trace | #766 (qqq) | C6 | #7 |
| B5 webhooks | #767 (qqq) | C7 | #8 |
| B6 workflows | #768 (qqq) | C8 | #9 |
| B7 geo-data | #769 (qqq) | C9 | #10 |
| B8 sftp-data-integration | #770 (qqq) | | |

Most qbit repos have issues disabled, so their tasks are tracked in QRun-IO/qqq.

## Waves

| Wave | Tasks | Needs |
|---|---|---|
| Q1 | A2, B1–B8, C1 | — |
| Q2 | C2 | B1–B8 merged, C1 |
| Q3 | A1 | ESB task 10 (qqq#748) merged |
| Q4 | C3 | A1, A2, C2; ESB tasks 8 and 10 (qqq#746, #748) |
| Q5 | C4, C5 | C3 |
| Q6 | C6, C8 | C4, C5 |
| Q7 | C7 | C6; ESB epic complete |
| Q8 | C9 | qqq 4.1 GA (ESB + Next default); James's go-ahead |
