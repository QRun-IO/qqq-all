#!/usr/bin/env python3
"""Boot a private full Compose stack, run HTTP smoke checks, and remove it."""

import json
import os
import secrets
import socket
import subprocess
import sys
import tempfile
import urllib.parse
from pathlib import Path

from smoke import check_full
from smoke_diagnostics import Diagnostics


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


def query_opensearch(command, environment, index_name, query):
    url = ("http://127.0.0.1:9200/" + urllib.parse.quote(index_name, safe="")
           + "/_search")
    result = subprocess.run(
        command + ["exec", "-T", "opensearch", "curl", "--fail", "--silent",
                   "--show-error", "--max-time", "5", "-H", "Content-Type: application/json",
                   "--data-binary", json.dumps(query), url],
        cwd=ROOT, env=environment, capture_output=True, text=True, timeout=15, check=False,
    )
    if result.returncode:
        raise AssertionError("OpenSearch query failed inside Compose")
    try:
        return json.loads(result.stdout)
    except ValueError:
        raise AssertionError("OpenSearch returned invalid JSON") from None


def prepare_sftp_import_directory(command, environment, username):
    path = f"/home/{username}/upload/imports"
    for args in (["mkdir", "-p", path], ["chown", "1001:1001", path]):
        result = subprocess.run(
            command + ["exec", "-T", "sftp"] + args,
            cwd=ROOT, env=environment, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            timeout=15, check=False,
        )
        if result.returncode:
            raise AssertionError("could not prepare SFTP import directory")


def main():
    if not (ROOT / "compose.yaml").is_file():
        raise AssertionError("C6 Compose is not integrated into this branch")
    values = demo_environment()
    project = "qqqallsmoke" + secrets.token_hex(4)
    environment = os.environ.copy()
    environment.update(values)
    os.environ.update(values)
    diagnostics = Diagnostics(ROOT / "target/smoke-diagnostics", environment)
    with tempfile.TemporaryDirectory(prefix="qqq-all-full-smoke-") as directory:
        env_file = Path(directory) / "compose.env"
        env_file.write_text("".join(f"{key}={value}\n" for key, value in values.items()))
        env_file.chmod(0o600)
        command = ["docker", "compose", "-p", project, "--env-file", str(env_file),
                   "-f", str(ROOT / "compose.yaml"), "-f", str(ROOT / "scripts/compose.smoke.yaml"),
                   "--profile", "full"]
        try:
            diagnostics.jar_hashes(ROOT)
            diagnostics.pull(command, ROOT, environment)
            started = diagnostics.run(command + ["up", "-d", "--build", "--wait", "--wait-timeout", "360"],
                                      ROOT, environment, timeout=480)
            diagnostics.write("startup.json", started)
            if started["exit_code"] != 0:
                raise AssertionError("full Compose stack did not become healthy")
            prepare_sftp_import_directory(command, environment, values.get("SFTP_USER", "qqq"))
            check_full(
                f"http://127.0.0.1:{values['QQQ_ALL_PORT']}",
                f"http://keycloak.localhost:{values['KEYCLOAK_PORT']}/realms/qqq-all",
                lambda index, query: query_opensearch(command, environment, index, query),
                120,
            )
        except (AssertionError, OSError, ValueError, subprocess.TimeoutExpired) as error:
            diagnostics.write("failure.json", {"type": type(error).__name__,
                                              "message": diagnostics.sanitize(str(error))})
            raise AssertionError(diagnostics.sanitize(str(error))) from None
        finally:
            try:
                diagnostics.collect(command, ROOT, environment)
            except Exception as error:
                # Evidence failure must not suppress the smoke error or private-stack cleanup.
                print(f"smoke diagnostics collection failed: {type(error).__name__}", file=sys.stderr)
            finally:
                cleanup = diagnostics.run(command + ["down", "--volumes", "--remove-orphans"],
                                          ROOT, environment, timeout=120)
                diagnostics.write("cleanup.json", cleanup)
                if cleanup["exit_code"] != 0 and sys.exc_info()[0] is None:
                    raise AssertionError("private Compose cleanup failed; see sanitized diagnostics")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, OSError, ValueError, subprocess.TimeoutExpired) as error:
        print(f"full smoke failed: {error}", file=sys.stderr)
        raise SystemExit(1) from None
