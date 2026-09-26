#!/usr/bin/env python3
"""Contract tests for the HTTP smoke checks, without starting the application."""

import json
import socketserver
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from smoke import SmokeClient, check_artemis_round_trip, check_core, check_rabbitmq, check_tables, CORE_TABLES


class SmokeServer(ThreadingHTTPServer):
    def server_bind(self):
        socketserver.TCPServer.server_bind(self)
        self.server_name = "localhost"
        self.server_port = self.server_address[1]


class DemoHandler(BaseHTTPRequestHandler):
    orders = 0
    product_name = "QRun Starter Kit"
    trace_label = "Sync Order - demo"

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
            return self.send_json({"records": [{"values": {"id": 100 + self.orders}}]})
        if self.path.startswith("/qqq/v1/table/") and self.path.endswith("/query"):
            table = self.path.split("/")[4]
            if table == "processTrace":
                records = [{"recordLabel": self.trace_label,
                            "values": {"id": i, "processUUID": f"trace-{i}"}}
                           for i in range(self.orders)]
            else:
                values = {
                    "customer": {"id": 1, "name": "Ada Lovelace"},
                    "order": {"id": 1, "orderNo": "ORD-1001"},
                    "orderLine": {"id": 1, "quantity": 2},
                    "product": {"id": 1, "name": self.product_name},
                }
                records = [{"values": values.get(table, {"id": 1})}]
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


if __name__ == "__main__":
    unittest.main()
