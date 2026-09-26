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

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md), the [Code of Conduct](CODE_OF_CONDUCT.md), and the [security policy](SECURITY.md).

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
