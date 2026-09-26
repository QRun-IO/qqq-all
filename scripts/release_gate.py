"""Fail-closed checks for the manual 4.1.0 release workflow."""

import argparse
import json
import re
import sys
from pathlib import Path
from xml.etree import ElementTree


QBITS = (
    "quick-search", "user-role-permissions", "customizable-table-views",
    "standard-process-trace", "webhooks", "workflows", "geo-data",
    "sftp-data-integration",
)


class GateError(ValueError):
    pass


def properties(path):
    root = ElementTree.parse(path).getroot()
    namespace = xml_namespace(root)
    values = root.find(f"{namespace}properties")
    if values is None:
        raise GateError(f"Missing Maven properties in {path}")
    return {child.tag.removeprefix(namespace): (child.text or "").strip() for child in values}


def xml_namespace(root):
    return root.tag.partition("}")[0] + "}" if root.tag.startswith("{") else ""


def coordinates(path):
    root = ElementTree.parse(path).getroot()
    namespace = xml_namespace(root)
    parent = root.find(f"{namespace}parent")

    def value(name):
        direct = root.findtext(f"{namespace}{name}")
        inherited = parent.findtext(f"{namespace}{name}") if parent is not None else None
        return direct or inherited

    return value("groupId"), value("artifactId"), value("version")


def validate_published_poms(parent_path, bom_path, version, local_bom=None):
    expected = (
        (parent_path, "qqq-all-parent"),
        (bom_path, "qqq-all-bom"),
    )
    for path, artifact in expected:
        if coordinates(path) != ("com.kingsrook.qqq", artifact, version):
            raise GateError(f"Published {artifact} POM is not the {version} release")
    published = properties(bom_path)
    if published.get("qqq.version") != version:
        raise GateError("Published BOM does not import QQQ 4.1.0")
    for name in QBITS:
        key = f"qbit-{name}.version"
        value = published.get(key, "")
        if not value or "SNAPSHOT" in value.upper():
            raise GateError(f"Published BOM {key} is not a release")
    if local_bom is not None:
        local = properties(local_bom)
        for key in ("qqq.version", *(f"qbit-{name}.version" for name in QBITS)):
            if published.get(key) != local.get(key):
                raise GateError(f"Published BOM {key} differs from release source")


def validate_poms(root, version):
    if version != "4.1.0":
        raise GateError("This release workflow is limited to 4.1.0")
    parent = properties(root / "pom.xml")
    bom = properties(root / "qqq-all-bom/pom.xml")
    if parent.get("revision") != version or bom.get("qqq.version") != version:
        raise GateError("Parent revision and QQQ BOM pin must both equal 4.1.0")
    for name in QBITS:
        key = f"qbit-{name}.version"
        value = bom.get(key, "")
        if not value or "SNAPSHOT" in value.upper():
            raise GateError(f"{key} must be a published non-snapshot version")
    for key, value in bom.items():
        if key.endswith(".version") and "SNAPSHOT" in value.upper():
            raise GateError(f"{key} still points to a snapshot")


def validate_smoke(run, jobs, sha):
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise GateError("Release SHA must be a full lowercase Git commit ID")
    expected = {
        "name": "Smoke", "path": ".github/workflows/smoke.yml",
        "head_sha": sha, "head_branch": "develop", "event": "push",
        "conclusion": "success",
    }
    for key, value in expected.items():
        if run.get(key) != value:
            raise GateError(f"Smoke run {key} must equal {value}")
    observed = {"core": False, "full": False}
    for job in jobs.get("jobs", []):
        name = job.get("name")
        if name in observed:
            if job.get("conclusion") != "success":
                raise GateError(f"Smoke job {name} did not pass")
            observed[name] = True
    if not all(observed.values()):
        raise GateError("Smoke run must include successful core and full jobs")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--sha", required=True)
    parser.add_argument("--smoke-run", type=Path, required=True)
    parser.add_argument("--smoke-jobs", type=Path, required=True)
    parser.add_argument("--published-parent", type=Path, required=True)
    parser.add_argument("--published-bom", type=Path, required=True)
    args = parser.parse_args()
    try:
        validate_poms(args.root, args.version)
        validate_smoke(json.loads(args.smoke_run.read_text()),
                       json.loads(args.smoke_jobs.read_text()), args.sha)
        validate_published_poms(args.published_parent, args.published_bom, args.version,
                                args.root / "qqq-all-bom/pom.xml")
    except (GateError, OSError, ElementTree.ParseError, json.JSONDecodeError) as error:
        print(f"Release gate failed: {error}", file=sys.stderr)
        return 1
    print("Local POM and exact-commit smoke gates passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
