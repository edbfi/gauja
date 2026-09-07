#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Run native login → cached user/title/artwork → offline render against pinned Seerr."""
import argparse
from contextlib import nullcontext
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import subprocess
import threading
from urllib.parse import urlsplit

from local_seerr import ROOT, initialized_server

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
        self.lock = threading.Lock()

    def handler(self):
        scenario = self
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                self.respond()

            def do_POST(self):
                self.respond()

            def respond(self):
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
                body = self.rfile.read(length) if length else None
                headers = {name: self.headers[name] for name in ("Content-Type", "Cookie", "Authorization", "X-Api-Key") if self.headers[name]}
                connection = HTTPConnection(scenario.backend.hostname, scenario.backend.port, timeout=20)
                try:
                    connection.request(self.command, self.path, body=body, headers=headers)
                    response = connection.getresponse()
                    payload = response.read()
                    self.send_response(response.status)
                    for name, value in response.getheaders():
                        if name.lower() not in {"connection", "transfer-encoding", "content-length"}:
                            self.send_header(name, value)
                    self.send_header("Content-Length", str(len(payload)))
                    self.end_headers()
                    self.wfile.write(payload)
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
        try:
            env = dict(os.environ, GAUJA_AUTH_SERVER=f"http://127.0.0.1:{server.server_port}", GAUJA_AUTH_CREDENTIALS=str(credentials.resolve()))
            if platform == "android":
                command = [str(ROOT / "apps/android/gradlew"), "--project-dir", str(ROOT / "apps/android"),
                           ":core:data:testDebugUnitTest", "--tests", "*HelloServerTest.liveInitializedSeerrHelloServer", "--rerun", "--quiet"]
            else:
                command = ["swift", "test", "--package-path", str(ROOT / "apps/ios/Packages/Data"), "--filter", "liveInitializedSeerrHelloServer"]
            subprocess.run(command, env=env, check=True)
            scenario.verify()
        finally:
            server.shutdown()
            thread.join()
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
