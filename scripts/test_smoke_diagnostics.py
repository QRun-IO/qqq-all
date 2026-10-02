#!/usr/bin/env python3
"""Failure evidence must survive cleanup without leaking runtime credentials."""

import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import run_full_smoke


class DiagnosticsTest(unittest.TestCase):
    def test_startup_failure_retains_redacted_evidence_before_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'compose.yaml').touch()
            values = {'QQQ_ALL_PORT': '20001', 'KEYCLOAK_PORT': '20002',
                      'POSTGRES_PASSWORD': 'private-password-123'}
            cleaned = []

            def run(args, **kwargs):
                if 'up' in args:
                    return subprocess.CompletedProcess(args, 1, 'postgres unhealthy private-password-123', '')
                if 'down' in args:
                    receipt = root / 'target/smoke-diagnostics/startup.json'
                    self.assertTrue(receipt.is_file(), 'diagnostics must exist before teardown')
                    cleaned.append(True)
                return subprocess.CompletedProcess(args, 0, '', '')

            with (mock.patch.object(run_full_smoke, 'ROOT', root),
                  mock.patch.object(run_full_smoke, 'demo_environment', return_value=values),
                  mock.patch.object(run_full_smoke.subprocess, 'run', side_effect=run),
                  mock.patch.dict(os.environ)):
                with self.assertRaisesRegex(AssertionError, 'did not become healthy'):
                    run_full_smoke.main()
            self.assertTrue(cleaned)
            output = (root / 'target/smoke-diagnostics/startup.json').read_text()
            self.assertIn('postgres unhealthy', output)
            self.assertNotIn('private-password-123', output)
            self.assertEqual(1, json.loads(output)['exit_code'])

    def test_http_failure_stays_sanitized_when_collection_and_cleanup_fail(self):
        self.diagnostics_module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'compose.yaml').touch()
            values = {'QQQ_ALL_PORT': '20001', 'KEYCLOAK_PORT': '20002',
                      'DB_PASSWORD': 'hidden-fixture'}
            def run(args, **kwargs):
                if 'down' in args:
                    raise subprocess.TimeoutExpired(args, 120, output='hidden-fixture')
                return subprocess.CompletedProcess(args, 0, '', '')
            with (mock.patch.object(run_full_smoke, 'ROOT', root),
                  mock.patch.object(run_full_smoke, 'demo_environment', return_value=values),
                  mock.patch.object(run_full_smoke.subprocess, 'run', side_effect=run),
                  mock.patch.object(run_full_smoke.Diagnostics, 'collect', side_effect=ValueError('hidden-fixture')),
                  mock.patch.object(run_full_smoke, 'check_full', side_effect=AssertionError('HTTP failed hidden-fixture')),
                  mock.patch.dict(os.environ)):
                with self.assertRaisesRegex(AssertionError, r'HTTP failed \[REDACTED\]'):
                    run_full_smoke.main()
            text = ''.join(f.read_text() for f in (root / 'target/smoke-diagnostics').glob('*.json'))
            self.assertNotIn('hidden-fixture', text)
            self.assertIn('TimeoutExpired', text)

    def test_startup_timeout_keeps_partial_sanitized_output_and_tears_down(self):
        self.diagnostics_module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'compose.yaml').touch()
            values = {'QQQ_ALL_PORT': '20001', 'KEYCLOAK_PORT': '20002',
                      'DB_PASSWORD': 'hidden-fixture'}
            cleanup = []
            def run(args, **kwargs):
                if 'up' in args:
                    raise subprocess.TimeoutExpired(args, 480, output=b'postgres unhealthy hidden-fixture')
                if 'down' in args:
                    cleanup.append(True)
                return subprocess.CompletedProcess(args, 0, '', '')
            with (mock.patch.object(run_full_smoke, 'ROOT', root),
                  mock.patch.object(run_full_smoke, 'demo_environment', return_value=values),
                  mock.patch.object(run_full_smoke.subprocess, 'run', side_effect=run),
                  mock.patch.dict(os.environ)):
                with self.assertRaisesRegex(AssertionError, 'did not become healthy'):
                    run_full_smoke.main()
            self.assertTrue(cleanup)
            receipt = json.loads((root / 'target/smoke-diagnostics/startup.json').read_text())
            self.assertEqual('TimeoutExpired', receipt['error'])
            self.assertIn('postgres unhealthy', receipt['output'])
            self.assertNotIn('hidden-fixture', receipt['output'])

    def diagnostics_module(self):
        try:
            import smoke_diagnostics
        except ImportError:
            self.fail('sanitized diagnostics collector is not implemented')
        return smoke_diagnostics

    def test_redacts_secret_forms_and_bounds_output_after_redaction(self):
        module = self.diagnostics_module()
        with tempfile.TemporaryDirectory() as directory:
            evidence = module.Diagnostics(Path(directory), {'DB_PASSWORD': 'a+/secret',
                                                           'GITHUB_TOKEN': 'token-1234'})
            output = evidence.sanitize('x' * 90000 + '\npassword="unknown-value" '
                                       'postgres://u:uri-secret@db/ Bearer bearer-secret '
                                       'a%2B%2Fsecret a+/secret token-1234 access_token=generated-token client_secret=generated-secret')
            for secret in ['unknown-value', 'uri-secret', 'bearer-secret',
                           'a%2B%2Fsecret', 'a+/secret', 'token-1234', 'generated-token', 'generated-secret']:
                self.assertFalse(secret in output, "credential remained in sanitized output")
            self.assertLessEqual(len(output), 65536)
            self.assertIn('[REDACTED]', output)

    def test_hashes_only_resolved_first_party_jars_not_other_cached_versions(self):
        module = self.diagnostics_module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / 'repository/com/kingsrook/qqq/qqq-esb/4.1.0-SNAPSHOT/qqq-esb.jar'
            jar.parent.mkdir(parents=True)
            jar.write_bytes(b'actual resolved bytes')
            (jar.parent / 'unused.jar').write_bytes(b'unused')
            manifest = root / 'qqq-all-app/target/smoke-classpath.txt'
            manifest.parent.mkdir(parents=True)
            manifest.write_text(str(jar))
            evidence = module.Diagnostics(root / 'target/smoke-diagnostics', {})
            evidence.jar_hashes(root)
            data = json.loads((evidence.directory / 'jars.json').read_text())
            self.assertEqual([{'coordinate': 'com.kingsrook.qqq:qqq-esb:4.1.0-SNAPSHOT',
                               'file': 'qqq-esb.jar',
                               'sha256': hashlib.sha256(b'actual resolved bytes').hexdigest()}], data['dependencies'])
            self.assertNotIn(str(root), json.dumps(data))

    def test_container_collection_is_selected_redacted_and_survives_command_failure(self):
        module = self.diagnostics_module()
        with tempfile.TemporaryDirectory() as directory:
            evidence = module.Diagnostics(Path(directory), {'DB_PASSWORD': 'secret-fixture'})
            container = 'a' * 64
            completed = 'd' * 64
            image = 'sha256:' + 'b' * 64
            commands = []
            def run(args, **kwargs):
                commands.append(args)
                if 'ps' in args:
                    return subprocess.CompletedProcess(args, 0, container + '\n' + completed + '\n', '')
                if 'inspect' in args and 'image' not in args:
                    self.assertIn('--format', args)
                    if args[-1] == completed:
                        return subprocess.CompletedProcess(args, 0, json.dumps({
                            'id': completed, 'service': 'mongo-init', 'image_id': image,
                            'status': 'exited', 'exit_code': 0, 'oom_killed': False,
                            'error': '', 'health': None}), '')
                    return subprocess.CompletedProcess(args, 0, json.dumps({
                        'id': container, 'service': 'postgres', 'image_id': image,
                        'status': 'exited', 'exit_code': 1, 'oom_killed': False,
                        'error': '', 'health': {'Status': 'unhealthy', 'Log': [
                            {'ExitCode': 1, 'Output': 'failed secret-fixture'}]}}), '')
                if 'image' in args:
                    return subprocess.CompletedProcess(args, 0, json.dumps({'id': image,
                        'repo_digests': ['postgres@sha256:' + 'c' * 64],
                        'architecture': 'amd64', 'os': 'linux'}), '')
                if 'logs' in args:
                    raise subprocess.TimeoutExpired(args, 15, output='secret-fixture')
                self.fail('unexpected command')
            with mock.patch.object(module.subprocess, 'run', side_effect=run):
                evidence.collect(['docker', 'compose', '-p', 'private'], Path(directory), {})
            text = '\n'.join(f.read_text() for f in Path(directory).glob('*.json'))
            self.assertNotIn('secret-fixture', text)
            self.assertIn('unhealthy', text)
            self.assertIn('amd64', text)
            states = json.loads((Path(directory) / 'services.json').read_text())['services']
            self.assertEqual('mongo-init', states[1]['service'])
            self.assertEqual(0, states[1]['exit_code'])
            self.assertIsNone(states[1]['health']['status'])
            self.assertIn('TimeoutExpired', text)
            self.assertFalse(any('config' in c or '--volumes' in c for c in commands))


if __name__ == '__main__':
    unittest.main()
