# qqq-all — Design (approved 2026-09-25)

## Goal
One repo that gives a fully running QQQ app with every feature configured and working out of the box. New public repo `QRun-IO/qqq-all`, Apache-2.0. Version tracks qqq (4.1.x); builds on 4.1.0-SNAPSHOT until 4.1 ships.

## What's in it
- **Stack BOM** — one tested version set: qqq modules, `qqq-esb`, `qqq-frontend-next`, the included qbits, JDBC drivers, broker clients.
- **Reference app** — every backend, qbit, and the ESB wired, with seeded demo data.
- **Compose file** with two profiles.
- **Image** on GHCR; CI boots the stack and runs smoke tests plus the ESB conformance suite.

## Profiles
- **`core`** — no containers, `java -jar`: H2, SQLite, local filesystem, embedded Artemis, mock auth.
- **`full`** — `docker compose --profile full up`: Postgres, MySQL, MongoDB (replica set), MinIO (S3), SFTP, OpenSearch, ActiveMQ Artemis, RabbitMQ, Mailpit, Keycloak (demo admin and user accounts).

## Qbits (v1)
- **In:** quick-search, user-role-permissions, customizable-table-views, standard-process-trace, webhooks, workflows, geo-data, sftp-data-integration.
- **Deferred:** crm (being rehabilitated for the Business Platform), wms (large), session-store (alpha), middleware-mcp (no auth), easypost / custom-apps / worm-audit (external keys or stubs).
- Each included qbit is re-pinned to `qbit-build-parent` 2.0.0 and qqq 4.1 (one PR per qbit repo). Quick-search also gets its index-drift fix and repo cleanup.

## Core prerequisites (qqq 4.1)
- **Launcher** — one entry point that starts the web server, scheduler, and ESB when configured. Every app gets it, not just qqq-all.
- **Fail-fast option** — a metadata producer that throws stops startup instead of logging a warning.

## Demo
- A small orders/customers domain that touches every feature: a table per backend, quick search on customers, order events on the ESB triggering a process, a workflow, webhooks, geo data, an SFTP import.
- Admin sees everything, including the ESB app; the demo user sees a permission-limited view.

## Limits
Local demo and reference only. Production still needs DNS, TLS, backups, monitoring, and HA brokers.

## Next
Approve → issues (qqq-all epic, 2 core issues in qqq, one re-pin issue per qbit) → agents, after ESB wave 1 lands.
