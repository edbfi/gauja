#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Exercise actual transport origin rejection, redirect blocking and cookie isolation."""
import argparse
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import subprocess
import threading
import xml.etree.ElementTree as ET
from tls_server import tls_server

ROOT = Path(__file__).resolve().parents[2]


def assert_android_results(path):
    report = ET.parse(path).getroot()
    if int(report.attrib["tests"]) < 2 or any(int(report.attrib.get(key, 0)) for key in ("failures", "errors", "skipped")):
        raise ValueError("Transport tests did not all execute successfully")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["android", "ios"])
    args = parser.parse_args()
    with tls_server() as (environment, tls_paths):
        exercise(args.platform, environment)
        if tls_paths != ["/tls"]:
            raise ValueError("TLS checks must send exactly one request with a matching pin and hostname")
    print(f"egress: {args.platform} transport checks passed")


def exercise(platform, environment):
    if platform == "android":
        subprocess.run([str(ROOT / "apps/android/gradlew"), "--project-dir", str(ROOT / "apps/android"),
                        ":core:network:testDebugUnitTest", "--tests", "*ProbeTransportTest", "--tests", "*TransportTlsTest", "--rerun-tasks", "--quiet"], env=dict(os.environ, **environment), check=True)
        assert_android_results(ROOT / "apps/android/core/network/build/test-results/testDebugUnitTest/TEST-app.gauja.core.network.ProbeTransportTest.xml")
    else:
        paths = []
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                paths.append(self.path)
                status = 302 if self.path == "/redirect" else 401 if self.path == "/401" else 200
                if self.path == "/imageproxy/tmdb/t/p/w342/gauja-test.png":
                    body = (ROOT / "design/assets/test/poster.png").read_bytes()
                elif self.path == "/cookie":
                    body = self.headers.get("Cookie", "no-cookie").encode()
                elif self.path == "/operator":
                    safe = (self.headers.get("X-Api-Key") == "synthetic-key" and
                            self.headers.get("Authorization", "").startswith("Basic ") and
                            not self.headers.get("Cookie") and not self.headers.get("X-API-User"))
                    body = b"operator-safe" if safe else b"operator-unsafe"
                else:
                    body = b"cookie-leaked" if self.headers.get("Cookie") else b"no-cookie"
                self.send_response(status)
                self.send_header("Content-Type", "image/png" if self.path.endswith("gauja-test.png") else "text/plain")
                self.send_header("Connection", "close")
                self.send_header("Location", "http://localhost:1/forbidden")
                if self.path.startswith("/session/"):
                    self.send_header("Set-Cookie", f"connect.sid={self.path[-1]}; Path=/; Max-Age=2592000")
                elif self.path == "/operator":
                    self.send_header("Set-Cookie", "connect.sid=must-ignore; Path=/")
                elif self.path == "/redirect":
                    self.send_header("Set-Cookie", "synthetic=test")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                env = dict(os.environ, **environment, GAUJA_EGRESS_SERVER=f"http://127.0.0.1:{server.server_port}")
                subprocess.run(["swift", "test", "--package-path", str(ROOT / "apps/ios/Packages/Network")], env=env, check=True)
                if Counter(paths) != Counter({"/redirect": 1, "/echo": 1, "/session/a": 1, "/session/b": 1, "/cookie": 3, "/401": 1, "/operator": 2, "/imageproxy/tmdb/t/p/w342/gauja-test.png": 2}):
                    raise ValueError("Unexpected or missing transport requests")
            finally:
                server.shutdown()
                worker.join()


if __name__ == "__main__":
    main()
