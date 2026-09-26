#!/usr/bin/env python3
"""Boot a private full Compose stack, run HTTP smoke checks, and remove it."""

import os
import secrets
import socket
import subprocess
import sys
import tempfile
from pathlib import Path

from smoke import check_full


ROOT = Path(__file__).resolve().parent.parent


def free_port(exclude=()):
    for _ in range(30):
        port = 20000 + secrets.randbelow(10000)
        if port in exclude:
            continue
        try:
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", port))
                return port
        except OSError:
            continue
    raise AssertionError("could not reserve a full smoke host port")


def demo_environment():
    values = {}
    for line in (ROOT / ".env.example").read_text().splitlines():
        if not line or line.startswith("#"):
            continue
        key, value = line.split("=", 1)
        if "PASSWORD" in key or "SECRET" in key or key == "MONGO_KEYFILE":
            value = secrets.token_urlsafe(24)
        values[key] = value
    app_port = free_port()
    values["QQQ_ALL_PORT"] = str(app_port)
    values["KEYCLOAK_PORT"] = str(free_port((app_port,)))
    return values


def published_port(command, service, target, environment):
    result = subprocess.run(command + ["port", service, str(target)],
                            cwd=ROOT, env=environment, capture_output=True, text=True,
                            timeout=15, check=False)
    if result.returncode:
        raise AssertionError(f"could not discover {service} host port")
    address = result.stdout.strip()
    if not address.startswith("127.0.0.1:") or not address.rsplit(":", 1)[1].isdigit():
        raise AssertionError(f"unexpected {service} host port")
    return int(address.rsplit(":", 1)[1])


def main():
    if not (ROOT / "compose.yaml").is_file():
        raise AssertionError("C6 Compose is not integrated into this branch")
    values = demo_environment()
    project = "qqqallsmoke" + secrets.token_hex(4)
    environment = os.environ.copy()
    environment.update(values)
    os.environ.update(values)
    with tempfile.TemporaryDirectory(prefix="qqq-all-full-smoke-") as directory:
        env_file = Path(directory) / "compose.env"
        env_file.write_text("".join(f"{key}={value}\n" for key, value in values.items()))
        env_file.chmod(0o600)
        command = ["docker", "compose", "-p", project, "--env-file", str(env_file),
                   "-f", str(ROOT / "compose.yaml"), "-f", str(ROOT / "scripts/compose.smoke.yaml"),
                   "--profile", "full"]
        try:
            started = subprocess.run(command + ["up", "-d", "--build", "--wait", "--wait-timeout", "360"],
                                     cwd=ROOT, env=environment, stdout=subprocess.DEVNULL,
                                     stderr=subprocess.DEVNULL, timeout=480, check=False)
            if started.returncode:
                raise AssertionError("full Compose stack did not become healthy")
            search_port = published_port(command, "opensearch", 9200, environment)
            rabbit_port = published_port(command, "rabbitmq", 15672, environment)
            check_full(
                f"http://127.0.0.1:{values['QQQ_ALL_PORT']}",
                f"http://keycloak.localhost:{values['KEYCLOAK_PORT']}/realms/qqq-all",
                f"http://127.0.0.1:{search_port}", f"http://127.0.0.1:{rabbit_port}", 120,
            )
        finally:
            subprocess.run(command + ["down", "--volumes", "--remove-orphans"],
                           cwd=ROOT, env=environment, stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL, timeout=120, check=False)


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, OSError, ValueError, subprocess.TimeoutExpired) as error:
        print(f"full smoke failed: {error}", file=sys.stderr)
        raise SystemExit(1) from None
