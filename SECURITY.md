# Security Policy

## Supported Versions

qqq-all versions track QQQ versions.

| Version | Supported | Notes |
| ------- | --------- | ----- |
| 4.1.x   | Yes       | In development on `develop` (`4.1.0-SNAPSHOT`) until QQQ 4.1 ships |

## Scope

qqq-all is a local demo and reference build. It is not a production deployment: production still needs DNS, TLS, backups, monitoring, and highly available brokers. Any demo credentials in this repository are for local use only and must be overridden before exposing a stack to a network.

Vulnerabilities in the QQQ framework or a qBit should be reported to that project. If you are unsure where an issue belongs, report it here and it will be routed.

## Reporting a Vulnerability

**Security vulnerabilities should NEVER be reported publicly.**

### Private Reporting

Use GitHub's private vulnerability reporting:
**[Report a vulnerability](https://github.com/QRun-IO/qqq-all/security/advisories/new)**

Or email: **security@qrun.io**

### What to Include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- qqq-all version and profile (`core` or `full`), Java version, OS, and Docker version if relevant

### Response Timeline

| Stage | Timeline |
|-------|----------|
| Initial response | 24 hours |
| Assessment | 3 business days |
| Resolution | Based on severity |

## For Contributors

- Dependencies must not introduce HIGH or CRITICAL vulnerabilities
- Secrets must never be committed
- Follow QQQ's secure coding practices ([CODE_STYLE.md](https://github.com/QRun-IO/qqq/blob/develop/CODE_STYLE.md))

## Contact

- **Security issues**: security@qrun.io
- **General contact**: contact@qrun.io
- **Organization**: [QRun-IO](https://github.com/QRun-IO)
