#!/usr/bin/env python3
"""Contract tests for the HTTP smoke checks, without starting the application."""

import http.cookiejar
import json
import os
import re
import shutil
import socketserver
import subprocess
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from unittest import mock

from smoke import SmokeClient, check_artemis_round_trip, check_core, check_rabbitmq, check_tables, CORE_TABLES
import run_full_smoke
from run_full_smoke import ROOT


class SmokeServer(ThreadingHTTPServer):
    def server_bind(self):
        socketserver.TCPServer.server_bind(self)
        self.server_name = "localhost"
        self.server_port = self.server_address[1]


class DemoHandler(BaseHTTPRequestHandler):
    orders = 0
    product_name = "QRun Starter Kit"
    trace_label = "Sync Order - demo"
    trace_order_id = None
    inserted_marker = None

    def log_message(self, *_args):
        pass

    def do_GET(self):
        if self.path == "/health":
            return self.send_json({"status": "UP"})
        if self.path == "/":
            body = b'<html><script src="/_next/static/chunks/demo.js"></script></html>'
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if self.path == "/_next/static/chunks/demo.js":
            body = b"console.log('demo');"
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        self.send_error(404)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        if self.path == "/data/order/":
            assert body["orderNo"].startswith("SMOKE-")
            type(self).orders += 1
            type(self).trace_order_id = 100 + self.orders
            type(self).inserted_marker = body["orderNo"]
            return self.send_json({"records": [{"values": {"id": 100 + self.orders}}]})
        if self.path.startswith("/qqq/v1/table/") and self.path.endswith("/query"):
            table = self.path.split("/")[4]
            if table == "processTrace":
                records = [{"recordLabel": self.trace_label,
                            "values": {"id": i, "processUUID": f"trace-{i}",
                                       "keyRecordId": self.trace_order_id}}
                           for i in range(self.orders)]
            else:
                values = {
                    "customer": {"id": 1, "name": "Ada Lovelace"},
                    "order": {"id": 1, "orderNo": "ORD-1001"},
                    "orderLine": {"id": 1, "quantity": 2},
                    "product": {"id": 1, "name": self.product_name},
                }
                records = [{"values": values.get(table, {"id": 1})}]
                if table == "order" and self.inserted_marker:
                    records.append({"values": {"id": 100 + self.orders,
                                                "orderNo": self.inserted_marker}})
            return self.send_json({"records": records})
        self.send_error(404)

    def send_json(self, value):
        data = json.dumps(value).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class SmokeTest(unittest.TestCase):
    def setUp(self):
        DemoHandler.orders = 0
        DemoHandler.trace_order_id = None
        DemoHandler.inserted_marker = None

    def test_core_runner_fails_promptly_when_jar_exits(self):
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "broken.jar"
            jar.write_bytes(b"not a jar")
            result = subprocess.run(
                [sys.executable, str(Path(__file__).with_name("run_core_smoke.py")),
                 str(jar), "--timeout", "1"], capture_output=True, text=True, timeout=5,
            )
            self.assertEqual(1, result.returncode)
            self.assertIn("packaged app exited", result.stderr)

    def test_ci_core_smokes_maven_final_artifact(self):
        pom = ET.parse(ROOT / "qqq-all-app" / "pom.xml")
        final_name = pom.findtext(".//{http://maven.apache.org/POM/4.0.0}build/"
                                  "{http://maven.apache.org/POM/4.0.0}finalName")
        workflow = (ROOT / ".github" / "workflows" / "smoke.yml").read_text()
        command = re.search(r"run: python3 scripts/run_core_smoke.py (\S+)", workflow)
        self.assertIsNotNone(command)
        self.assertEqual(f"qqq-all-app/target/{final_name}.jar", command.group(1))

    def test_standalone_full_smoke_requires_explicit_search_endpoint(self):
        result = subprocess.run(
            [sys.executable, str(Path(__file__).with_name("smoke.py")), "full",
             "--base-url", "http://127.0.0.1:1"],
            capture_output=True, text=True, timeout=5,
        )
        self.assertEqual(1, result.returncode)
        self.assertIn("run_full_smoke.py", result.stderr)

    def test_full_runner_reserves_distinct_non_ephemeral_app_ports(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".env.example").write_text(
                "QQQ_ALL_PORT=8080\nKEYCLOAK_PORT=8081\nPOSTGRES_PASSWORD=example\n")
            with mock.patch.object(run_full_smoke, "ROOT", root):
                values = run_full_smoke.demo_environment()
            app_port = int(values["QQQ_ALL_PORT"])
            keycloak_port = int(values["KEYCLOAK_PORT"])
            self.assertTrue(20000 <= app_port < 30000)
            self.assertTrue(20000 <= keycloak_port < 30000)
            self.assertNotEqual(app_port, keycloak_port)
            self.assertNotEqual("example", values["POSTGRES_PASSWORD"])

    def test_full_runner_supplies_generated_credentials_to_http_checks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "compose.yaml").write_text("services: {}\n")
            values = {"QQQ_ALL_PORT": "20001", "KEYCLOAK_PORT": "20002",
                      "DEMO_USER_PASSWORD": "generated-user",
                      "DEMO_ADMIN_PASSWORD": "generated-admin",
                      "RABBITMQ_PASSWORD": "generated-rabbit"}

            def verify_credentials(*_args):
                for key in ("DEMO_USER_PASSWORD", "DEMO_ADMIN_PASSWORD", "RABBITMQ_PASSWORD"):
                    self.assertEqual(values[key], os.environ[key])

            with (mock.patch.object(run_full_smoke, "ROOT", root),
                  mock.patch.object(run_full_smoke, "demo_environment", return_value=values),
                  mock.patch.object(run_full_smoke, "published_port", return_value=20003),
                  mock.patch.object(run_full_smoke.subprocess, "run",
                                    return_value=subprocess.CompletedProcess([], 0)),
                  mock.patch.object(run_full_smoke, "check_full", side_effect=verify_credentials) as check,
                  mock.patch.dict(os.environ, {}, clear=False)):
                run_full_smoke.main()
                check.assert_called_once()

    def test_full_runner_queries_private_opensearch_service(self):
        command = ["docker", "compose", "-p", "smoke"]
        with mock.patch.object(run_full_smoke.subprocess, "run",
                               return_value=subprocess.CompletedProcess([], 0,
                                   stdout='{"hits":{"hits":[]}}')) as invoke:
            result = run_full_smoke.query_opensearch(command, {}, "customers", {"query": {}})
        self.assertEqual([], result["hits"]["hits"])
        args = invoke.call_args.args[0]
        self.assertEqual(["exec", "-T", "opensearch", "curl"], args[len(command):len(command) + 4])
        self.assertIn("http://127.0.0.1:9200/customers/_search", args)

    def test_full_runner_fails_when_private_opensearch_query_fails(self):
        with mock.patch.object(run_full_smoke.subprocess, "run",
                               return_value=subprocess.CompletedProcess([], 22, stdout="")):
            with self.assertRaisesRegex(AssertionError, "OpenSearch query failed"):
                run_full_smoke.query_opensearch(["docker", "compose"], {}, "customers", {"query": {}})

    def test_full_runner_prepares_sftp_import_directory(self):
        command = ["docker", "compose", "-p", "smoke"]
        with mock.patch.object(run_full_smoke.subprocess, "run",
                               return_value=subprocess.CompletedProcess([], 0)) as invoke:
            run_full_smoke.prepare_sftp_import_directory(command, {}, "qqq")
        self.assertEqual([
            command + ["exec", "-T", "sftp", "mkdir", "-p", "/home/qqq/upload/imports"],
            command + ["exec", "-T", "sftp", "chown", "1001:1001", "/home/qqq/upload/imports"],
        ], [call.args[0] for call in invoke.call_args_list])

    def test_full_runner_fails_when_sftp_fixture_cannot_be_prepared(self):
        with mock.patch.object(run_full_smoke.subprocess, "run", side_effect=[
            subprocess.CompletedProcess([], 0), subprocess.CompletedProcess([], 1)]):
            with self.assertRaisesRegex(AssertionError, "SFTP import directory"):
                run_full_smoke.prepare_sftp_import_directory(["docker", "compose"], {}, "qqq")

    def test_oidc_cookie_is_sent_only_to_loopback_http_app(self):
        from smoke import allow_loopback_http_session

        class SecureSession(DemoHandler):
            def do_GET(self):
                if self.path in ("/login", "/login-plain"):
                    data = b"ok"
                    self.send_response(200)
                    secure = "; Secure" if self.path == "/login" else ""
                    self.send_header("Set-Cookie", "sessionUUID=test-session; Path=/; HttpOnly" + secure)
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                    return
                if self.path == "/protected":
                    if "sessionUUID=test-session" not in self.headers.get("Cookie", ""):
                        return self.send_error(403)
                    return self.send_json({"ok": True})
                return super().do_GET()

        server = SmokeServer(("127.0.0.1", 0), SecureSession)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = SmokeClient(f"http://127.0.0.1:{server.server_port}")
            client.request("/login")
            with self.assertRaisesRegex(AssertionError, "HTTP 403"):
                client.request("/protected")
            allow_loopback_http_session(client)
            self.assertTrue(client.json("/protected")["ok"])
            plain = SmokeClient(f"http://127.0.0.1:{server.server_port}")
            plain.request("/login-plain")
            allow_loopback_http_session(plain)
            self.assertTrue(plain.json("/protected")["ok"])
            remote = SmokeClient("http://example.com")
            with self.assertRaisesRegex(AssertionError, "loopback"):
                allow_loopback_http_session(remote)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_keycloak_secure_auth_cookie_is_sent_on_loopback_http(self):
        from smoke import allow_loopback_http_cookies

        class SecureKeycloak(DemoHandler):
            def do_GET(self):
                if self.path == "/auth":
                    data = b"login"
                    self.send_response(200)
                    self.send_header("Set-Cookie", "AUTH_SESSION_ID=test-auth; Path=/; Secure; HttpOnly")
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                    return
                return super().do_GET()

            def do_POST(self):
                if self.path == "/login":
                    if "AUTH_SESSION_ID=test-auth" not in self.headers.get("Cookie", ""):
                        return self.send_error(400)
                    return self.send_json({"ok": True})
                return super().do_POST()

        server = SmokeServer(("127.0.0.1", 0), SecureKeycloak)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            base = f"http://127.0.0.1:{server.server_port}"
            cookies = http.cookiejar.CookieJar()
            browser = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))
            browser.open(base + "/auth", timeout=2).close()
            with self.assertRaises(urllib.error.HTTPError) as denied:
                browser.open(urllib.request.Request(base + "/login", data=b"x"), timeout=2)
            denied.exception.close()
            allow_loopback_http_cookies(cookies, base)
            with browser.open(urllib.request.Request(base + "/login", data=b"x"), timeout=2) as response:
                self.assertEqual({"ok": True}, json.load(response))
            with self.assertRaisesRegex(AssertionError, "loopback"):
                allow_loopback_http_cookies(cookies, "http://example.com")
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_core_checks_public_http_behavior(self):
        server = SmokeServer(("127.0.0.1", 0), DemoHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = SmokeClient(f"http://127.0.0.1:{server.server_port}")
            check_core(client, timeout=2)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_missing_backend_records_fail(self):
        server = SmokeServer(("127.0.0.1", 0), DemoHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = SmokeClient(f"http://127.0.0.1:{server.server_port}")
            with self.assertRaisesRegex(AssertionError, "missing expected customer"):
                client.require_record("customer", "name", "Grace Hopper")
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_core_rejects_wrong_filesystem_seed(self):
        class WrongProduct(DemoHandler):
            product_name = "Unrelated Product"

        server = SmokeServer(("127.0.0.1", 0), WrongProduct)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = SmokeClient(f"http://127.0.0.1:{server.server_port}")
            with self.assertRaisesRegex(AssertionError, "missing expected product"):
                check_tables(client, CORE_TABLES)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_artemis_rejects_unrelated_trace(self):
        class WrongTrace(DemoHandler):
            trace_label = "Unrelated Process - demo"

        server = SmokeServer(("127.0.0.1", 0), WrongTrace)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = SmokeClient(f"http://127.0.0.1:{server.server_port}")
            with self.assertRaisesRegex(AssertionError, "Artemis.*syncOrder"):
                check_artemis_round_trip(client, timeout=0.3)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_artemis_returns_the_new_trace_uuid(self):
        server = SmokeServer(("127.0.0.1", 0), DemoHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = SmokeClient(f"http://127.0.0.1:{server.server_port}")
            self.assertEqual("trace-0", check_artemis_round_trip(client, timeout=2))
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_artemis_rejects_trace_for_another_order(self):
        class WrongOrderTrace(DemoHandler):
            trace_order_id = 999

            def do_POST(self):
                if self.path == "/data/order/":
                    body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                    assert body["orderNo"].startswith("SMOKE-")
                    type(self).orders += 1
                    return self.send_json({"records": [{"values": {"id": 101}}]})
                return super().do_POST()

        server = SmokeServer(("127.0.0.1", 0), WrongOrderTrace)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = SmokeClient(f"http://127.0.0.1:{server.server_port}")
            with self.assertRaisesRegex(AssertionError, "Artemis.*syncOrder"):
                check_artemis_round_trip(client, timeout=0.3)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_rabbitmq_rejects_unrelated_completion(self):
        class WrongCompletion(DemoHandler):
            def do_POST(self):
                if self.path.startswith("/api/queues/"):
                    event = {"type": "qqq.process.other.completed",
                             "data": {"processName": "other", "processUUID": "trace-1"}}
                    return self.send_json([{"payload": json.dumps(event)}])
                return super().do_POST()

        server = SmokeServer(("127.0.0.1", 0), WrongCompletion)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with self.assertRaisesRegex(AssertionError, "RabbitMQ syncOrder completion"):
                check_rabbitmq(f"http://127.0.0.1:{server.server_port}", "qqq", "pw", 0.3, "trace-1")
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_rabbitmq_rejects_wrong_process_uuid(self):
        class WrongRun(DemoHandler):
            def do_POST(self):
                if self.path.startswith("/api/queues/"):
                    event = {"type": "qqq.process.syncOrder.completed",
                             "data": {"processName": "syncOrder", "processUUID": "other-run"}}
                    return self.send_json([{"payload": json.dumps(event)}])
                return super().do_POST()

        server = SmokeServer(("127.0.0.1", 0), WrongRun)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with self.assertRaisesRegex(AssertionError, "RabbitMQ syncOrder completion"):
                check_rabbitmq(f"http://127.0.0.1:{server.server_port}", "qqq", "pw", 0.3, "trace-0")
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_rabbitmq_accepts_matching_process_uuid(self):
        class MatchingRun(DemoHandler):
            def do_POST(self):
                if self.path.startswith("/api/queues/"):
                    event = {"type": "qqq.process.syncOrder.completed",
                             "data": {"processName": "syncOrder", "processUUID": "trace-0"}}
                    return self.send_json([{"payload": json.dumps(event)}])
                return super().do_POST()

        server = SmokeServer(("127.0.0.1", 0), MatchingRun)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            check_rabbitmq(f"http://127.0.0.1:{server.server_port}", "qqq", "pw", 2, "trace-0")
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    @unittest.skipUnless((ROOT / "compose.yaml").is_file() and shutil.which("docker"),
                         "C6 Compose integration is not available")
    def test_full_smoke_keeps_opensearch_private_and_uses_ephemeral_service_ports(self):
        command = ["docker", "compose", "--env-file", str(ROOT / ".env.example"),
                   "-f", str(ROOT / "compose.yaml"),
                   "-f", str(ROOT / "scripts/compose.smoke.yaml"),
                   "--profile", "full", "config", "--format", "json"]
        result = subprocess.run(command, capture_output=True, text=True, timeout=15)
        self.assertEqual(0, result.returncode, "full smoke Compose config failed")
        services = json.loads(result.stdout)["services"]
        self.assertNotIn("ports", services["opensearch"])
        for service, target in (("rabbitmq", 15672),
                                ("artemis", 8161), ("mailpit", 8025), ("minio", 9001)):
            with self.subTest(service=service):
                ports = services[service]["ports"]
                self.assertEqual(1, len(ports))
                self.assertEqual(target, ports[0]["target"])
                self.assertEqual("127.0.0.1", ports[0]["host_ip"])
                self.assertFalse(ports[0].get("published"))


if __name__ == "__main__":
    unittest.main()
