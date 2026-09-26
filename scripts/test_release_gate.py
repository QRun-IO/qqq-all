"""Release gate checks use fixtures so the workflow can fail closed before publication."""

import tempfile
import unittest
from pathlib import Path

from release_gate import GateError, validate_poms, validate_published_poms, validate_smoke


RELEASE_SHA = "a" * 40
QBITS = (
    "quick-search", "user-role-permissions", "customizable-table-views",
    "standard-process-trace", "webhooks", "workflows", "geo-data",
    "sftp-data-integration",
)


class ReleaseGateTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / "qqq-all-bom").mkdir()
        self.write_poms("4.1.0", "4.1.0", "1.2.3")

    def write_poms(self, revision, qqq_version, qbit_version):
        (self.root / "pom.xml").write_text(
            f"<project><properties><revision>{revision}</revision></properties></project>"
        )
        (self.root / "qqq-all-bom/pom.xml").write_text(
            "<project><properties>"
            f"<qqq.version>{qqq_version}</qqq.version>"
            + "".join(f"<qbit-{name}.version>{qbit_version}</qbit-{name}.version>" for name in QBITS)
            + "</properties></project>"
        )

    def test_release_poms_require_ga_versions(self):
        validate_poms(self.root, "4.1.0")
        for revision, qqq_version, qbit_version in (
            ("4.1.0-SNAPSHOT", "4.1.0", "1.2.3"),
            ("4.1.0", "4.1.0-SNAPSHOT", "1.2.3"),
            ("4.1.0", "4.1.0", "1.2.3-SNAPSHOT"),
        ):
            with self.subTest(revision=revision, qqq=qqq_version, qbit=qbit_version):
                self.write_poms(revision, qqq_version, qbit_version)
                with self.assertRaises(GateError):
                    validate_poms(self.root, "4.1.0")

    def test_smoke_run_must_match_release_sha_and_both_jobs(self):
        run = {
            "name": "Smoke", "path": ".github/workflows/smoke.yml",
            "head_sha": RELEASE_SHA, "head_branch": "develop", "event": "push",
            "conclusion": "success",
        }
        jobs = {"jobs": [
            {"name": "core", "conclusion": "success"},
            {"name": "full", "conclusion": "success"},
        ]}
        validate_smoke(run, jobs, RELEASE_SHA)
        for changed_run, changed_jobs in (
            ({**run, "head_sha": "b" * 40}, jobs),
            ({**run, "conclusion": "failure"}, jobs),
            ({**run, "path": ".github/workflows/other.yml"}, jobs),
            (run, {"jobs": [jobs["jobs"][0]]}),
            (run, {"jobs": [jobs["jobs"][0], {"name": "full", "conclusion": "skipped"}]}),
        ):
            with self.subTest(run=changed_run, jobs=changed_jobs):
                with self.assertRaises(GateError):
                    validate_smoke(changed_run, changed_jobs, RELEASE_SHA)

    def test_published_parent_and_bom_must_be_release_poms(self):
        parent = self.root / "published-parent.pom"
        bom = self.root / "published-bom.pom"
        parent.write_text("<project><groupId>com.kingsrook.qqq</groupId>"
                          "<artifactId>qqq-all-parent</artifactId><version>4.1.0</version></project>")
        bom.write_text("<project><parent><groupId>com.kingsrook.qqq</groupId>"
                       "<artifactId>qqq-all-parent</artifactId><version>4.1.0</version></parent>"
                       "<artifactId>qqq-all-bom</artifactId><properties><qqq.version>4.1.0</qqq.version>"
                       + "".join(f"<qbit-{name}.version>1.2.3</qbit-{name}.version>" for name in QBITS)
                       + "</properties></project>")
        validate_published_poms(parent, bom, "4.1.0", self.root / "qqq-all-bom/pom.xml")
        bom.write_text(bom.read_text().replace("4.1.0", "4.1.0-SNAPSHOT"))
        with self.assertRaises(GateError):
            validate_published_poms(parent, bom, "4.1.0", self.root / "qqq-all-bom/pom.xml")
        bom.write_text(bom.read_text().replace("4.1.0-SNAPSHOT", "4.1.0")
                       .replace("<qbit-webhooks.version>1.2.3</qbit-webhooks.version>",
                                "<qbit-webhooks.version>1.2.4</qbit-webhooks.version>"))
        with self.assertRaises(GateError):
            validate_published_poms(parent, bom, "4.1.0", self.root / "qqq-all-bom/pom.xml")


if __name__ == "__main__":
    unittest.main()
