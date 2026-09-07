#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Exercise actual transport origin rejection, redirect blocking and cookie isolation."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import subprocess
import threading
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def assert_android_results(path):
    report = ET.parse(path).getroot()
    if int(report.attrib["tests"]) < 2 or any(int(report.attrib.get(key, 0)) for key in ("failures", "errors", "skipped")):
        raise ValueError("Transport tests did not all execute successfully")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["android", "ios"])
    args = parser.parse_args()
    if args.platform == "android":
        subprocess.run([str(ROOT / "apps/android/gradlew"), "--project-dir", str(ROOT / "apps/android"),
                        ":core:network:testDebugUnitTest", "--tests", "*ProbeTransportTest", "--rerun-tasks", "--quiet"], check=True)
        assert_android_results(ROOT / "apps/android/core/network/build/test-results/testDebugUnitTest/TEST-app.gauja.core.network.ProbeTransportTest.xml")
    else:
        paths = []
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                paths.append(self.path)
                self.send_response(302 if self.path == "/redirect" else 200)
                self.send_header("Location", "http://localhost:1/forbidden")
                self.send_header("Set-Cookie", "synthetic=test")
                self.end_headers()
                self.wfile.write(b"cookie-leaked" if self.headers.get("Cookie") else b"no-cookie")

        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                env = dict(os.environ, GAUJA_EGRESS_SERVER=f"http://127.0.0.1:{server.server_port}")
                subprocess.run(["swift", "test", "--package-path", str(ROOT / "apps/ios/Packages/Network")], env=env, check=True)
                if paths != ["/redirect", "/echo"]:
                    raise ValueError("Unexpected or missing transport requests")
            finally:
                server.shutdown()
                worker.join()
    print(f"egress: {args.platform} transport checks passed")


if __name__ == "__main__":
    main()
