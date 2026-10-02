#!/usr/bin/env python3
"""Package digest-pinned Compose with the tracked full-profile infrastructure."""

import argparse
import json
import os
import re
import shutil
import subprocess
import tarfile
from pathlib import Path

from release_gate import validate_version


def create_bundle(root, version, sha, image, output):
    validate_version(version)
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Source SHA must be a full commit ID")
    if not re.fullmatch(r"ghcr\.io/qrun-io/qqq-all@sha256:[0-9a-f]{64}", image):
        raise ValueError("Release image must be the qqq-all GHCR digest")
    environment = {key: value for key, value in os.environ.items() if not key.startswith("COMPOSE_")}
    rendered = subprocess.run(
        ["docker", "compose", "--env-file", ".env.example", "--profile", "*", "-f", "compose.yaml",
         "config", "--no-interpolate", "--no-env-resolution", "--no-path-resolution", "--format", "json"],
        cwd=root, env=environment, capture_output=True, text=True, check=True,
    )
    compose = json.loads(rendered.stdout)
    compose.pop("x-app-image", None)
    for name in ("app-core", "app-full"):
        app = compose["services"][name]
        app.pop("build", None)
        app["image"] = image
        app["pull_policy"] = "always"
    compose["services"]["minio"]["image"] = "qqq-all-minio:" + version
    name = "qqq-all-" + version
    bundle = output / name
    bundle.mkdir(parents=True, exist_ok=False)
    (bundle / "compose.yaml").write_text(json.dumps(compose, indent=2) + "\n")
    tracked = subprocess.check_output(["git", "-C", str(root), "ls-files", "--", "infra"], text=True).splitlines()
    for filename in (".env.example", "LICENSE", "NOTICE", *tracked):
        source = root / filename
        if source.is_symlink() or not source.is_file():
            raise ValueError(f"Bundle input must be a regular file: {filename}")
        target = bundle / filename
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    (bundle / "release.json").write_text(json.dumps(
        {"version": version, "source_sha": sha, "image": image}, indent=2) + "\n")
    (bundle / "README.md").write_text(f"""# qqq-all {version}

Extract this complete bundle and run commands from this directory. The application uses
`{image}` (source `{sha}`). No application source, Maven or app build is required.
Docker with Compose v2 is required. Copy `.env.example` to `.env` and replace
the demonstration passwords before sharing the environment. All fixture values remain
demo-only; retain the infra directory next to compose.yaml.

```bash
cp .env.example .env
docker compose -p qqq-all-demo --profile core up -d --wait --no-build
```

For the full profile, stop core first. The included MinIO Dockerfile still builds locally
and needs network access; PostgreSQL/MySQL/Mongo initialization and the Keycloak realm
use the bundled infra files. External service images also need network access.

```bash
docker compose -p qqq-all-demo --profile core down
docker compose -p qqq-all-demo --profile full build minio
docker compose -p qqq-all-demo --profile full up -d --wait --no-build
```

Use a distinct Compose project name for each installation. The application listens on
http://localhost:8080 by default. The full-profile identity provider uses
http://keycloak.localhost:8081; that hostname must resolve to loopback on the client.
Ports and fixture settings are in `.env.example`. `docker compose ... down` retains
named data volumes; remove volumes only when intentionally resetting that installation.
The standalone compose.yaml asset needs this bundle's infra and environment template
for full-profile operation. See release.json for the exact application identity.
""")
    archive = output / (name + ".tar.gz")
    with tarfile.open(archive, "x:gz") as handle:
        handle.add(bundle, arcname=name)
    return archive


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--sha", required=True)
    parser.add_argument("--image", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(create_bundle(args.root.resolve(), args.version, args.sha, args.image, args.output.resolve()))


if __name__ == "__main__":
    main()
