#!/usr/bin/env python3
"""Start the packaged core jar, smoke its HTTP API, and always stop it."""

import argparse
import os
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from smoke import CORE_ADMIN_SESSION, SmokeClient, check_core


def free_port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def await_health(client, process, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise AssertionError("packaged app exited before becoming healthy")
        try:
            if client.json("/health").get("status") == "UP":
                return
        except (AssertionError, OSError, ValueError):
            pass
        time.sleep(0.1)
    raise AssertionError("timed out waiting for packaged app health")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--timeout", type=int, default=90)
    args = parser.parse_args()
    if not args.jar.is_file():
        raise AssertionError("packaged jar was not found")

    with tempfile.TemporaryDirectory(prefix="qqq-all-core-smoke-") as directory:
        port = free_port()
        environment = os.environ.copy()
        environment.update({
            "QQQ_ALL_PROFILE": "core",
            "QQQ_ALL_PORT": str(port),
            "QQQ_ALL_DATA_DIR": str(Path(directory) / "data"),
        })
        with (Path(directory) / "app.log").open("wb") as output:
            process = subprocess.Popen(
                ["java", "-jar", str(args.jar.resolve())],
                env=environment, stdout=output, stderr=subprocess.STDOUT,
            )
            try:
                client = SmokeClient(f"http://127.0.0.1:{port}", CORE_ADMIN_SESSION)
                await_health(client, process, args.timeout)
                check_core(client, args.timeout)
                if process.poll() is not None:
                    raise AssertionError("packaged app exited during smoke checks")
            finally:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, OSError, ValueError) as error:
        print(f"core smoke failed: {error}", file=sys.stderr)
        raise SystemExit(1) from None
