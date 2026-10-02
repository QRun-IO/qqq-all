#!/usr/bin/env python3
"""Small, sanitized receipts for the private full-smoke stack only."""

import base64
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
from pathlib import Path


LIMIT = 65536
CONTAINER_FORMAT = ('{"id":{{json .Id}},"service":{{json (index .Config.Labels '
                    '"com.docker.compose.service")}},"image_id":{{json .Image}},'
                    '"status":{{json .State.Status}},"exit_code":{{json .State.ExitCode}},'
                    '"oom_killed":{{json .State.OOMKilled}},"error":{{json .State.Error}},'
                    '"health":{{if index .State "Health"}}{{json (index .State "Health")}}{{else}}null{{end}}}')
IMAGE_FORMAT = ('{"id":{{json .Id}},"repo_digests":{{json .RepoDigests}},'
                '"architecture":{{json .Architecture}},"os":{{json .Os}}}')


class Diagnostics:
    def __init__(self, directory, environment):
        self.directory = directory
        self.secrets = set()
        for key, value in environment.items():
            if value and re.search(r'PASSWORD|PASSWD|SECRET|TOKEN|KEYFILE|ACCESS_KEY|PRIVATE_KEY|CREDENTIAL|AUTHORIZATION', key, re.I):
                self.secrets.update((value, urllib.parse.quote(value, safe=''),
                                     urllib.parse.quote_plus(value),
                                     base64.b64encode(value.encode()).decode()))

    def sanitize(self, text):
        if isinstance(text, bytes):
            text = text.decode('utf-8', errors='replace')
        text = text or ''
        text = re.sub(r'\x1b\[[0-?]*[ -/]*[@-~]', '', text)
        for secret in sorted(self.secrets, key=len, reverse=True):
            text = text.replace(secret, '[REDACTED]')
        text = re.sub(r'(?i)(\b[\w.-]{0,80}(?:password|passwd|secret|token|authorization|credential|api[_-]?key|access[_-]?key)\b["\s]*[:=]\s*)("[^"]*"|\x27[^\x27]*\x27|[^\s,;]+)', r'\1[REDACTED]', text)
        text = re.sub(r'(?i)\b(Bearer|Basic)\s+[^\s,;]+', r'\1 [REDACTED]', text)
        text = re.sub(r'(?i)\b([a-z][a-z0-9+.-]*://)[^/\s@]+@', r'\1[REDACTED]@', text)
        if len(text) > LIMIT:
            text = '[...truncated...]\n' + text[-(LIMIT - 18):]
        return text

    def write(self, name, data):
        # Redact strings before JSON encoding, so escaped secrets cannot escape filtering.
        def clean(value):
            if isinstance(value, str):
                return self.sanitize(value)
            if isinstance(value, dict):
                return {key: clean(item) for key, item in value.items()}
            if isinstance(value, list):
                return [clean(item) for item in value]
            return value
        try:
            self.directory.mkdir(parents=True, exist_ok=True)
            path = self.directory / name
            path.write_text(json.dumps(clean(data), indent=2) + '\n')
            path.chmod(0o600)
        except OSError as error:
            print(f"smoke diagnostics write failed: {type(error).__name__}", file=sys.stderr)

    def run(self, args, root, environment, timeout=15):
        try:
            result = subprocess.run(args, cwd=root, env=environment, capture_output=True,
                                    text=True, timeout=timeout, check=False)
            return {'exit_code': result.returncode,
                    'output': self.sanitize((result.stdout or '') + (result.stderr or ''))}
        except subprocess.TimeoutExpired as error:
            return {'exit_code': None, 'error': 'TimeoutExpired',
                    'output': self.sanitize(self.sanitize(error.stdout) + self.sanitize(error.stderr))}
        except OSError as error:
            return {'exit_code': None, 'error': type(error).__name__, 'output': ''}

    def jar_hashes(self, root):
        data = {'dependencies': []}
        manifest = root / 'qqq-all-app/target/smoke-classpath.txt'
        if manifest.is_file():
            for name in sorted(set(manifest.read_text().strip().split(os.pathsep))):
                path = Path(name)
                match = re.search(r'/com/kingsrook/(qqq|qbits)/([^/]+)/([^/]+)/[^/]+\.jar$', path.as_posix())
                if not match:
                    continue
                group, artifact, version = match.groups()
                entry = {'coordinate': f'com.kingsrook.{group}:{artifact}:{version}', 'file': path.name}
                if path.is_file():
                    with path.open('rb') as stream:
                        entry['sha256'] = hashlib.file_digest(stream, 'sha256').hexdigest()
                else:
                    entry['error'] = 'resolved JAR missing'
                data['dependencies'].append(entry)
        else:
            data['error'] = 'resolved classpath missing; run the full-smoke Maven command'
        app = root / 'qqq-all-app/target/qqq-all-app.jar'
        if app.is_file():
            with app.open('rb') as stream:
                data['application_sha256'] = hashlib.file_digest(stream, 'sha256').hexdigest()
        self.write('jars.json', data)

    def pull(self, command, root, environment):
        receipt = {'pulls': []}
        try:
            # Keep the uninterpolated model in memory only; retain no service environment.
            model = subprocess.run(command + ['config', '--format', 'json', '--no-interpolate',
                                              '--no-env-resolution'], cwd=root, env=environment,
                                   capture_output=True, text=True, timeout=15, check=False)
            if model.returncode:
                raise ValueError('Compose image selection failed')
            services = json.loads(model.stdout)['services']
            if not isinstance(services, dict) or not 1 <= len(services) <= 32:
                raise ValueError('unexpected service inventory')
            selected = [(name, service['image']) for name, service in sorted(services.items())
                        if 'build' not in service]
            if any(not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_.-]*', name)
                   or not isinstance(image, str) or '${' in image for name, image in selected):
                raise ValueError('unresolved service image')
        except (KeyError, TypeError, ValueError, OSError, subprocess.TimeoutExpired) as error:
            receipt['selection_error'] = type(error).__name__
            self.write('pulls.json', receipt)
            raise AssertionError('could not select Compose service images; see pulls.json') from None
        deadline = time.monotonic() + 480
        for service, image in selected:
            first = image.split('/')[0]
            registry = first if '/' in image and ('.' in first or ':' in first or first == 'localhost') else 'docker.io'
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                result = {'exit_code': None, 'error': 'pull-stage budget exhausted', 'output': ''}
            else:
                result = self.run(command + ['--parallel', '1', '--progress', 'plain', 'pull',
                                             '--ignore-buildable', '--policy', 'missing', service],
                                  root, environment, timeout=min(120, remaining))
            receipt['pulls'].append({'service': service, 'image': image,
                                     'registry': registry, 'result': result})
            self.write('pulls.json', receipt)
            if result['exit_code'] != 0:
                raise AssertionError(f'Compose pull failed for {service} ({image}); see pulls.json')

    def collect(self, command, root, environment):
        deadline = time.monotonic() + 60
        def run(args):
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                return {'exit_code': None, 'error': 'collection budget exhausted', 'output': ''}
            return self.run(args, root, environment, timeout=min(15, remaining))
        inventory = run(command + ['ps', '-a', '-q'])
        ids = [line for line in inventory['output'].splitlines()
               if re.fullmatch(r'[a-f0-9]{12,64}', line)][:32]
        services, images = [], {}
        for container in ids:
            result = run(['docker', 'inspect', '--format', CONTAINER_FORMAT, container])
            try:
                raw = json.loads(result['output'])
                state = {key: raw.get(key) for key in ('id', 'service', 'image_id', 'status',
                                                       'exit_code', 'oom_killed', 'error')}
                health = raw.get('health') or {}
                state['health'] = {'status': health.get('Status'),
                                  'checks': [{key: check.get(key) for key in ('Start', 'End', 'ExitCode', 'Output')}
                                             for check in health.get('Log', [])[-5:]]}
                services.append(state)
                image = state['image_id']
                if isinstance(image, str) and re.fullmatch(r'sha256:[a-f0-9]{64}', image) and image not in images:
                    found = run(['docker', 'image', 'inspect', '--format', IMAGE_FORMAT, image])
                    try:
                        details = json.loads(found['output'])
                        images[image] = {key: details.get(key) for key in ('id', 'repo_digests', 'architecture', 'os')}
                    except (ValueError, TypeError, AttributeError):
                        images[image] = {'error': 'image identity unavailable', 'command': found}
            except (ValueError, TypeError, AttributeError):
                services.append({'id': container, 'error': 'container state unavailable', 'command': result})
            self.write(f'container-{container[:12]}.json',
                       run(['docker', 'logs', '--timestamps', '--tail', '100', container]))
        self.write('services.json', {'inventory': inventory, 'services': services, 'images': images})
