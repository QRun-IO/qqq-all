"""Exercise the delivered archive with Compose, without running any containers."""

import json
import os
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
IMAGE = "ghcr.io/qrun-io/qqq-all@sha256:" + "a" * 64


class ReleaseBundleTest(unittest.TestCase):
    def test_bundle_uses_digest_and_keeps_full_profile_infra_without_app_build(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(["python3", str(ROOT / "scripts/release_bundle.py"),
                                     "--root", str(ROOT), "--version", "4.1.0-RC.1",
                                     "--sha", "b" * 40, "--image", IMAGE, "--output", directory],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            archive = Path(directory) / "qqq-all-4.1.0-RC.1.tar.gz"
            extracted = Path(directory) / "extracted"
            with tarfile.open(archive) as bundle:
                names = bundle.getnames()
                self.assertIn("qqq-all-4.1.0-RC.1/infra/minio/Dockerfile", names)
                self.assertIn("qqq-all-4.1.0-RC.1/infra/keycloak/realm.json", names)
                self.assertIn("qqq-all-4.1.0-RC.1/.env.example", names)
                self.assertNotIn("qqq-all-4.1.0-RC.1/.env", names)
                self.assertNotIn("qqq-all-4.1.0-RC.1/Dockerfile", names)
                bundle.extractall(extracted, filter="data")
            root = extracted / "qqq-all-4.1.0-RC.1"
            environment = {key: value for key, value in os.environ.items() if not key.startswith("COMPOSE_")}
            result = subprocess.run(["docker", "compose", "--env-file", ".env.example", "--profile", "*",
                                     "-f", "compose.yaml", "config", "--format", "json"],
                                    cwd=root, env=environment, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            config = json.loads(result.stdout)
            original_result = subprocess.run(["docker", "compose", "--env-file", ".env.example", "--profile", "*",
                                              "-f", "compose.yaml", "config", "--format", "json"],
                                             cwd=ROOT, env=environment, capture_output=True, text=True, check=True)
            original = json.loads(original_result.stdout.replace(str(ROOT.resolve()), str(root.resolve())))
            for name, service in original["services"].items():
                if name in ("app-core", "app-full"):
                    service.pop("build")
                    service.update(image=IMAGE, pull_policy="always")
                if name == "minio":
                    service["image"] = "qqq-all-minio:4.1.0-RC.1"
                self.assertEqual(service, config["services"][name], name)
            for service in ("app-core", "app-full"):
                self.assertEqual(IMAGE, config["services"][service]["image"])
                self.assertNotIn("build", config["services"][service])
            self.assertEqual((root / "infra/minio").resolve(), Path(config["services"]["minio"]["build"]["context"]).resolve())
            for service in config["services"].values():
                for volume in service.get("volumes", []):
                    if volume["type"] == "bind":
                        self.assertTrue(Path(volume["source"]).exists(), volume)
            manifest = json.loads((root / "release.json").read_text())
            self.assertEqual("4.1.0-RC.1", manifest["version"])
            self.assertEqual("b" * 40, manifest["source_sha"])
            self.assertEqual(IMAGE, manifest["image"])

    def test_bundle_rejects_mutable_image_and_unapproved_version(self):
        with tempfile.TemporaryDirectory() as directory:
            for version, image in (("4.1.0-RC.2", IMAGE), ("4.1.0-RC.1", "ghcr.io/qrun-io/qqq-all:develop"),
                                   ("4.1.0", "ghcr.io/other/qqq-all@sha256:" + "a" * 64)):
                result = subprocess.run(["python3", str(ROOT / "scripts/release_bundle.py"),
                                         "--root", str(ROOT), "--version", version, "--sha", "b" * 40,
                                         "--image", image, "--output", directory], capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode)
            self.assertEqual([], list(Path(directory).iterdir()))
