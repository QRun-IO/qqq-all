# Contributing to qqq-all

qqq-all is open source and welcomes contributions. It is the reference build of [QQQ](https://github.com/QRun-IO/qqq) with every feature configured, so most framework changes belong in QQQ or the relevant qBit; changes here wire those pieces together, add demo data, or fix the build. For framework guides, see the [QQQ Wiki](https://github.com/QRun-IO/qqq/wiki).

## How to Contribute

1. **Report issues.** Found a bug or want a feature in the demo stack? [Open an issue](https://github.com/QRun-IO/qqq-all/issues/new/choose). Framework bugs go to [QRun-IO/qqq](https://github.com/QRun-IO/qqq/issues).
2. **Contribute code.** Branch from `develop` as `feature/<issue>-<short-description>` and open the pull request against `develop`.
3. **Improve docs.** Documentation fixes are welcome in the same way as code.

**Key requirements:**
- Java 21 and Maven 3.8+
- Follow QQQ's [Code Review Standards](https://github.com/QRun-IO/qqq/wiki/Code-Review-Standards)
- Add or update tests for any behavior you change
- Use [conventional commit](https://www.conventionalcommits.org/) messages
- Never commit secrets; demo credentials must be marked demo-only and overridable by environment variables

## Development Setup

```bash
git clone git@github.com:QRun-IO/qqq-all.git
cd qqq-all
mvn -B verify
```

qqq-all tracks the QQQ version. Until QQQ 4.1 ships, the build uses `4.1.0-SNAPSHOT` artifacts from the [Sonatype Central snapshot repository](https://central.sonatype.com/repository/maven-snapshots/), which the parent `pom.xml` already declares.

## Contribution Checklist

- [ ] `mvn -B verify` passes locally
- [ ] Tests cover the change
- [ ] Code follows QQQ's code style
- [ ] Commit messages follow the conventional commit format
- [ ] Docs updated where behavior changed

## Getting Help

- **[QQQ Wiki](https://github.com/QRun-IO/qqq/wiki)**: framework guides
- **[Issues](https://github.com/QRun-IO/qqq-all/issues)**: search existing issues or open a new one
- **[Discussions](https://github.com/QRun-IO/qqq/discussions)**: ask questions

## Legal Notice

When contributing to this project, you must agree that you have authored 100% of the content, that you have the necessary rights to the content, and that the content you contribute may be provided under the project license ([Apache-2.0](LICENSE)).

Please follow our [Code of Conduct](CODE_OF_CONDUCT.md). Report security issues privately as described in [SECURITY.md](SECURITY.md).
