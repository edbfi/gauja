#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Run native login → cached user/title/artwork → offline render against pinned Seerr."""
import argparse
from contextlib import nullcontext
from http.client import HTTPConnection, HTTPException
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import threading
import time
import uuid
from urllib.parse import urlsplit

from local_seerr import ROOT, initialized_server
from ios_hello import run as run_ios

OPERATIONS = {
    ("POST", "/api/v1/auth/local"), ("GET", "/api/v1/auth/me"), ("POST", "/api/v1/auth/logout"),
    ("GET", "/api/v1/status?checkUpdateAvailable=false"), ("GET", "/api/v1/settings/public"),
}
ARTWORK = "/imageproxy/tmdb/t/p/w342/gauja-test.png"


def local_address(base):
    value = urlsplit(base)
    if value.scheme != "http" or value.hostname not in {"127.0.0.1", "localhost"} or value.username or value.path or value.query or value.fragment:
        raise ValueError("Acceptance tests require a local HTTP server")
    return value


class Scenario:
    def __init__(self, base):
        self.backend = local_address(base)
        self.requests = []
        self.offline = False
        self.violations = 0
        self.responses = []
        self.lock = threading.Lock()
        self.started = time.monotonic()
        self.events = []

    def record(self, stage, **fields):
        with self.lock:
            if len(self.events) < 128:
                self.events.append(dict(stage=stage, elapsedSeconds=time.monotonic() - self.started, **fields))

    def retain_evidence(self, platform, completed):
        health = None
        if not completed:
            started = time.monotonic()
            connection = HTTPConnection(self.backend.hostname, self.backend.port, timeout=2)
            try:
                connection.request("GET", "/api/v1/status?checkUpdateAvailable=false")
                response = connection.getresponse()
                payload = json.loads(response.read(4096))
                health = {"status": response.status,
                          "supportedVersion": isinstance(payload, dict) and payload.get("version") == "3.4.1"}
            except (OSError, HTTPException, ValueError) as error:
                health = {"errorType": type(error).__name__}
            finally:
                connection.close()
            health["elapsedSeconds"] = time.monotonic() - started
        with self.lock:
            evidence = {"platform": platform, "completed": completed, "offline": self.offline,
                        "violations": self.violations, "events": list(self.events),
                        "eventsLimitReached": len(self.events) == 128, "backendHealthAfterFailure": health}
        directory = Path(os.environ.get("RUNNER_TEMP", ROOT / ".cache")) / "native-ci"
        directory.mkdir(parents=True, exist_ok=True)
        (directory / ("hello-diagnostics-" + uuid.uuid4().hex + ".json")).write_text(json.dumps(evidence, indent=2) + "\n")

    def handler(self):
        scenario = self
        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"
            def log_message(self, *args):
                pass

            def do_GET(self):
                self.respond()

            def do_POST(self):
                self.respond()

            def respond(self):
                # Never retain raw URLs, request bodies, credentials or arbitrary header values.
                route = self.path if (self.command, self.path) in OPERATIONS or self.path in {ARTWORK, "/__gauja_test/offline"} else "unrecognized"
                scenario.record("proxy_received", method=self.command, route=route)
                with scenario.lock:
                    if self.command == "POST" and self.path == "/__gauja_test/offline":
                        scenario.offline = True
                        self.send_response(204)
                        self.end_headers()
                        return
                    if scenario.offline:
                        scenario.violations += 1
                        self.send_error(503)
                        return
                    scenario.requests.append((self.command, self.path))
                if self.command == "GET" and self.path == ARTWORK:
                    if any(self.headers.get(name) for name in ("Cookie", "Authorization", "X-Api-Key", "X-API-User")):
                        with scenario.lock:
                            scenario.violations += 1
                        self.send_error(400)
                        return
                    body = (ROOT / "design/assets/test/poster.png").read_bytes()
                    self.send_response(200)
                    self.send_header("Content-Type", "image/png")
                    self.send_header("Content-Length", str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
                    return
                if (self.command, self.path) not in OPERATIONS:
                    self.send_error(404)
                    return
                try:
                    length = int(self.headers.get("Content-Length", 0))
                except ValueError:
                    self.send_error(400)
                    return
                if not 0 <= length <= 16384:
                    self.send_error(413)
                    return
                scenario.record("body_read_started", route=route, contentLength=length,
                                chunked=self.headers.get("Transfer-Encoding", "").lower() == "chunked",
                                expectContinue=self.headers.get("Expect", "").lower() == "100-continue")
                body = self.rfile.read(length) if length else None
                scenario.record("body_read_finished", route=route, bytesRead=len(body) if body else 0)
                headers = {name: self.headers[name] for name in ("Content-Type", "Cookie", "Authorization", "X-Api-Key") if self.headers[name]}
                connection = HTTPConnection(scenario.backend.hostname, scenario.backend.port, timeout=20)
                try:
                    scenario.record("upstream_send_started", route=route)
                    connection.request(self.command, self.path, body=body, headers=headers)
                    scenario.record("upstream_sent", route=route)
                    response = connection.getresponse()
                    scenario.record("upstream_headers_received", route=route, status=response.status)
                    payload = response.read()
                    scenario.record("upstream_body_received", route=route, bytesRead=len(payload))
                    with scenario.lock:
                        scenario.responses.append((self.path, response.status, length, self.headers.get("Transfer-Encoding")))
                    self.send_response(response.status)
                    for name, value in response.getheaders():
                        if name.lower() not in {"connection", "transfer-encoding", "content-length"}:
                            self.send_header(name, value)
                    self.send_header("Content-Length", str(len(payload)))
                    self.end_headers()
                    self.wfile.write(payload)
                    scenario.record("proxy_forwarded", route=route, status=response.status)
                except (OSError, HTTPException) as error:
                    scenario.record("proxy_error", route=route, errorType=type(error).__name__)
                    raise
                finally:
                    connection.close()
        return Handler

    def verify(self):
        required = {("POST", "/api/v1/auth/local"), ("GET", "/api/v1/auth/me"), ("GET", ARTWORK)}
        if not self.offline or self.violations or not required.issubset(self.requests):
            raise ValueError("Native hello-server did not complete login, cache fill and zero-egress offline rendering")


def run(platform, base, credentials):
    scenario = Scenario(base)
    with ThreadingHTTPServer(("127.0.0.1", 0), scenario.handler()) as server:
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        completed = False
        try:
            env = dict(os.environ, GAUJA_AUTH_SERVER=f"http://127.0.0.1:{server.server_port}", GAUJA_AUTH_CREDENTIALS=str(credentials.resolve()))
            if platform == "android":
                command = [str(ROOT / "apps/android/gradlew"), "--project-dir", str(ROOT / "apps/android"),
                           ":core:data:testDebugUnitTest", "--tests", "*HelloServerTest.liveInitializedSeerrHelloServer", "--rerun", "--quiet"]
            try:
                if platform == "android":
                    subprocess.run(command, env=env, check=True)
                else:
                    run_ios(env)
            except subprocess.CalledProcessError:
                print("hello-server: native command failed; retaining sanitized diagnostics")
                raise
            scenario.verify()
            completed = True
        finally:
            server.shutdown()
            thread.join()
            scenario.retain_evidence(platform, completed)
    print(f"hello-server: {platform} native session, cached user/title/artwork and zero-egress offline render passed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["android", "ios"])
    parser.add_argument("--base", help="Reuse an initialized local test server")
    parser.add_argument("--credentials", type=Path)
    args = parser.parse_args()
    if bool(args.base) != bool(args.credentials):
        parser.error("--base and --credentials must be supplied together")
    context = nullcontext((args.base, args.credentials)) if args.base else initialized_server()
    with context as (base, credentials):
        if not args.base:
            subprocess.run([str(ROOT / "tools/contract/python.sh"), str(ROOT / "tools/ci/local-auth-contract.py"),
                            "--base", base, "--credentials", str(credentials), "--initialize"], check=True)
        run(args.platform, base, credentials)
