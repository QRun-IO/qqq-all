#!/usr/bin/env python3
"""Black-box HTTP smoke checks for the qqq-all reference application."""

import argparse
import base64
import hashlib
import html.parser
import http.cookiejar
import json
import os
import re
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


CORE_TABLES = ("customer", "order", "orderLine", "product")
CORE_SEEDS = (
    ("customer", "name", "Ada Lovelace"),
    ("order", "orderNo", "ORD-1001"),
    ("orderLine", "quantity", 2),
    ("product", "name", "QRun Starter Kit"),
)
FULL_TABLES = CORE_TABLES + (
    "warehouseCustomer", "supplierOrder", "shipment", "document", "externalImportFile",
    "SFTPImportSourceFileTable",
)
CORE_ADMIN_SESSION = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"


class LoginForm(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.action = None
        self.values = {}
        self.inside = False

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "form" and values.get("id") == "kc-form-login":
            self.action = values.get("action")
            self.inside = True
        elif tag == "input" and self.inside and values.get("name"):
            self.values[values["name"]] = values.get("value", "")

    def handle_endtag(self, tag):
        if tag == "form":
            self.inside = False


class StopAtCallback(urllib.request.HTTPRedirectHandler):
    def __init__(self, callback):
        self.callback = callback

    def redirect_request(self, request, fp, code, msg, headers, url):
        if url.startswith(self.callback + "?"):
            return None
        return super().redirect_request(request, fp, code, msg, headers, url)


def wait_for(label, predicate, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if predicate():
                return
        except (OSError, TimeoutError, AssertionError, ValueError):
            pass
        time.sleep(0.25)
    raise AssertionError(f"timed out waiting for {label}")


class SmokeClient:
    def __init__(self, base_url, session_id=None):
        self.base_url = base_url.rstrip("/")
        self.session_id = session_id
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))

    def request(self, path, body=None, method=None):
        data = None if body is None else json.dumps(body).encode()
        headers = {"Content-Type": "application/json"} if data is not None else {}
        if self.session_id:
            headers["Cookie"] = "sessionId=" + self.session_id
        request = urllib.request.Request(
            self.base_url + path, data=data, method=method,
            headers=headers,
        )
        try:
            with self.opener.open(request, timeout=5) as response:
                if response.status != 200:
                    raise AssertionError(f"{path}: HTTP {response.status}")
                return response.read()
        except urllib.error.HTTPError as error:
            status = error.code
            error.close()
            raise AssertionError(f"{path}: HTTP {status}") from None

    def json(self, path, body=None):
        return json.loads(self.request(path, body))

    def records(self, table):
        result = self.json(f"/qqq/v1/table/{table}/query", {})
        records = result.get("records")
        if not isinstance(records, list):
            raise AssertionError(f"{table}: query did not return records")
        return records

    def require_record(self, table, field, value):
        if not any(record.get("values", {}).get(field) == value for record in self.records(table)):
            raise AssertionError(f"missing expected {table} {field}")


def allow_loopback_http_session(client):
    parsed = urllib.parse.urlsplit(client.base_url)
    if parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost"):
        raise AssertionError("OIDC HTTP cookie adjustment requires a loopback app")
    cookies = [cookie for cookie in client.cookies if cookie.name == "sessionUUID"]
    if len(cookies) != 1:
        raise AssertionError("OIDC did not set exactly one session cookie")
    if cookies[0].secure:
        cookies[0].secure = False


def allow_loopback_http_cookies(cookies, url):
    parsed = urllib.parse.urlsplit(url)
    host = parsed.hostname or ""
    if parsed.scheme != "http" or not (host in ("127.0.0.1", "localhost")
                                           or host.endswith(".localhost")):
        raise AssertionError("OIDC HTTP cookie adjustment requires a loopback host")
    for cookie in cookies:
        if cookie.secure and cookie.domain.lstrip(".") == host:
            cookie.secure = False


def check_health_and_dashboard(client, timeout):
    wait_for("health", lambda: client.json("/health").get("status") == "UP", timeout)
    html = client.request("/").decode()
    match = re.search(r'["\'](/_next/static/[^"\']+\.(?:js|css))["\']', html)
    if not match:
        raise AssertionError("dashboard did not reference a Next asset")
    if not client.request(match.group(1)):
        raise AssertionError("Next asset was empty")
    print("health and Next asset: OK")


def check_tables(client, tables, require_seed=True):
    for table in tables:
        records = client.records(table)
        if require_seed and not records:
            raise AssertionError(f"{table}: no seeded records")
    if require_seed:
        for table, field, value in CORE_SEEDS:
            client.require_record(table, field, value)
    else:
        client.require_record("customer", "name", "Ada Lovelace")
    print("backend table queries: OK")


def trigger_order(client):
    before = {record.get("values", {}).get("id") for record in client.records("processTrace")}
    marker = "SMOKE-" + uuid.uuid4().hex[:12]
    response = client.json("/data/order/", {
        "orderNo": marker, "customerId": 1, "status": "NEW"
    })
    inserted = response.get("records", [])
    order_id = inserted[0].get("values", {}).get("id") if inserted else None
    if not order_id:
        raise AssertionError("order insert did not return an ID")
    return before, order_id, marker


def check_artemis_round_trip(client, timeout):
    before, order_id, marker = trigger_order(client)
    process_uuid = None

    def new_sync_trace():
        nonlocal process_uuid
        for record in client.records("processTrace"):
            values = record.get("values", {})
            if (values.get("id") not in before
                    and record.get("recordLabel", "").startswith("Sync Order -")
                    and values.get("keyRecordId") == order_id
                    and values.get("processUUID")):
                process_uuid = values["processUUID"]
                return True
        return False

    wait_for("Artemis syncOrder trace",
             new_sync_trace, timeout)
    client.require_record("order", "orderNo", marker)
    print("Artemis order event round trip: OK")
    return process_uuid


def check_core(client, timeout):
    check_health_and_dashboard(client, timeout)
    check_tables(client, CORE_TABLES)
    check_artemis_round_trip(client, timeout)


def oidc_login(client, keycloak_url, username, password):
    """Complete the browser authorization-code flow and establish a QQQ cookie."""
    try:
        discovery_url = keycloak_url.rstrip("/") + "/.well-known/openid-configuration"
        with urllib.request.urlopen(discovery_url, timeout=5) as response:
            authorization_endpoint = json.load(response)["authorization_endpoint"]
        verifier = secrets.token_urlsafe(48)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
        state = secrets.token_urlsafe(24)
        callback = client.base_url + "/"
        query = urllib.parse.urlencode({
            "client_id": "qqq-all", "response_type": "code", "scope": "openid email profile",
            "redirect_uri": callback, "state": state, "code_challenge": challenge,
            "code_challenge_method": "S256",
        })
        browser_cookies = http.cookiejar.CookieJar()
        browser = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(browser_cookies), StopAtCallback(callback)
        )
        with browser.open(authorization_endpoint + "?" + query, timeout=10) as response:
            form = LoginForm()
            form.feed(response.read().decode())
        if not form.action:
            raise AssertionError("Keycloak login form missing")
        if urllib.parse.urlsplit(authorization_endpoint).scheme == "http":
            allow_loopback_http_cookies(browser_cookies, authorization_endpoint)
        form.values.update({"username": username, "password": password})
        request = urllib.request.Request(form.action, data=urllib.parse.urlencode(form.values).encode())
        try:
            with browser.open(request, timeout=10) as response:
                raise AssertionError(f"Keycloak did not redirect after login (HTTP {response.status})")
        except urllib.error.HTTPError as response:
            if response.code not in (302, 303):
                status = response.code
                response.close()
                raise AssertionError(f"Keycloak rejected the login (HTTP {status})") from None
            location = response.headers.get("Location", "")
            response.close()
        parameters = urllib.parse.parse_qs(urllib.parse.urlsplit(location).query)
        if parameters.get("state") != [state] or not parameters.get("code"):
            raise AssertionError("OIDC callback did not contain a valid code and state")
        result = client.json("/manageSession", {
            "code": parameters["code"][0], "redirectUri": callback, "codeVerifier": verifier
        })
        if not result.get("uuid") or not any(cookie.name == "sessionUUID" for cookie in client.cookies):
            raise AssertionError("QQQ did not establish an OIDC session")
        if urllib.parse.urlsplit(client.base_url).scheme == "http":
            allow_loopback_http_session(client)
    except (OSError, KeyError, ValueError, urllib.error.HTTPError) as error:
        raise AssertionError("OIDC login did not complete") from None
    print(f"OIDC {username} login: OK")


def http_query_index(search_url, index_name, query):
    request = urllib.request.Request(
        search_url.rstrip("/") + "/" + urllib.parse.quote(index_name, safe="") + "/_search",
        data=json.dumps(query).encode(), headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.load(response)


def check_quick_search(client, query_index, index_name, timeout):
    result = client.json("/processes/quickSearchFullReindex/run", {})
    if result.get("exception"):
        raise AssertionError("quick-search reindex failed")

    def finds_customer():
        query = {"query": {"bool": {"must": {"match": {"searchableText": "Ada"}},
                                    "filter": {"term": {"sourceTable": "customer"}}}}}
        hits = query_index(index_name, query)["hits"]["hits"]
        return any("Ada Lovelace" in json.dumps(hit.get("_source", {})) for hit in hits)

    wait_for("quick search seeded customer", finds_customer, timeout)
    print("OpenSearch customer quick search: OK")


def check_rabbitmq(client, timeout, process_uuid):
    def message_arrived():
        offset = 0
        while True:
            suffix = "" if offset == 0 else f"?offset={offset}"
            page = client.json("/qqq/v1/esb/messages/orderSyncEvents" + suffix)
            messages = page.get("messages", [])
            for message in messages:
                event = message.get("event")
                if not isinstance(event, dict):
                    continue
                data = event.get("data")
                if (event.get("type") == "qqq.process.syncOrder.completed"
                        and isinstance(data, dict)
                        and data.get("processName") == "syncOrder"
                        and data.get("processUUID") == process_uuid):
                    return True
            if not page.get("hasMore"):
                return False
            if not messages:
                raise ValueError("ESB browse returned an empty page with more messages")
            offset += len(messages)

    wait_for("RabbitMQ syncOrder completion", message_arrived, timeout)
    print("RabbitMQ completion publication: OK")


def check_full(base_url, keycloak_url, query_index, timeout):
    viewer = SmokeClient(base_url)
    check_health_and_dashboard(viewer, timeout)
    oidc_login(viewer, keycloak_url, "demo-user", os.environ["DEMO_USER_PASSWORD"])
    viewer.require_record("customer", "name", "Ada Lovelace")
    admin = SmokeClient(base_url)
    oidc_login(admin, keycloak_url, "demo-admin", os.environ["DEMO_ADMIN_PASSWORD"])
    check_tables(admin, FULL_TABLES, require_seed=False)
    check_quick_search(admin, query_index, os.environ.get("QQQ_ALL_OPENSEARCH_INDEX", "qqq-all-customers"), timeout)
    process_uuid = check_artemis_round_trip(admin, timeout)
    check_rabbitmq(admin, timeout, process_uuid)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=("core", "full"))
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--timeout", type=int, default=90)
    parser.add_argument("--keycloak-url", default="http://keycloak.localhost:8081/realms/qqq-all")
    parser.add_argument("--opensearch-url")
    args = parser.parse_args()
    client = SmokeClient(args.base_url, CORE_ADMIN_SESSION if args.profile == "core" else None)
    if args.profile == "core":
        check_core(client, args.timeout)
    else:
        if not args.opensearch_url:
            raise AssertionError("full profile needs --opensearch-url; use run_full_smoke.py for private Compose")
        check_full(args.base_url, args.keycloak_url,
                   lambda index, query: http_query_index(args.opensearch_url, index, query),
                   args.timeout)


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, OSError, ValueError, KeyError) as error:
        print(f"smoke failed: {error}", file=sys.stderr)
        raise SystemExit(1) from None
