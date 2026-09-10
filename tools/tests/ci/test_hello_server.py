# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
import importlib.util
import json
import subprocess
import tempfile
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
from pathlib import Path
import sys
import threading
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools/ci"))
spec = importlib.util.spec_from_file_location("hello_server", ROOT / "tools/ci/hello-server.py")
hello = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hello)
sys.path.pop(0)


class HelloServerTests(unittest.TestCase):
    def test_remote_or_credential_bearing_backend_is_rejected(self):
        for base in ("https://example.com", "http://user:password@localhost", "http://localhost/path", "http://localhost?key=private"):
            with self.assertRaises(ValueError):
                hello.Scenario(base)

    def test_missing_calls_offline_marker_and_extra_egress_fail(self):
        scenario = hello.Scenario("http://127.0.0.1:5055")
        with self.assertRaises(ValueError):
            scenario.verify()
        scenario.requests = [("POST", "/api/v1/auth/local"), ("GET", "/api/v1/auth/me"), ("GET", hello.ARTWORK)]
        scenario.offline = True
        scenario.verify()
        scenario.violations = 1
        with self.assertRaises(ValueError):
            scenario.verify()

    def test_artwork_credentials_and_offline_requests_never_reach_backend(self):
        scenario = hello.Scenario("http://127.0.0.1:5055")
        with ThreadingHTTPServer(("127.0.0.1", 0), scenario.handler()) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                with patch.object(hello, "HTTPConnection") as backend:
                    for method, path, headers, expected in [
                        ("GET", hello.ARTWORK, {"Cookie": "connect.sid=synthetic"}, 400),
                        ("POST", "/__gauja_test/offline", {}, 204),
                        ("GET", hello.ARTWORK, {}, 503),
                    ]:
                        connection = HTTPConnection("127.0.0.1", server.server_port)
                        connection.request(method, path, headers=headers)
                        response = connection.getresponse()
                        self.assertEqual(expected, response.status)
                        response.read()
                        connection.close()
                    backend.assert_not_called()
                    self.assertEqual(2, scenario.violations)
            finally:
                server.shutdown()
                worker.join()

    def test_native_failure_retains_request_stages_without_secrets(self):
        secret = "private-request-and-response-value"
        def native(environment):
            from urllib.parse import urlsplit
            address = urlsplit(environment["GAUJA_AUTH_SERVER"])
            connection = HTTPConnection(address.hostname, address.port, timeout=2)
            try:
                connection.request("POST", "/api/v1/auth/local", body=secret,
                                   headers={"Authorization": secret, "Cookie": secret})
                response = connection.getresponse()
                self.assertEqual(response.status, 200)
                response.read()
            finally:
                connection.close()
            raise subprocess.CalledProcessError(65, ["xcodebuild"])

        with tempfile.TemporaryDirectory() as temporary:
            with patch.dict(hello.os.environ, {"RUNNER_TEMP": temporary}), patch.object(hello, "run_ios", side_effect=native):
                with patch.object(hello, "HTTPConnection") as backend:
                    response = backend.return_value.getresponse.return_value
                    response.status = 200
                    response.read.return_value = json.dumps({"version": "3.4.1", "secret": secret}).encode()
                    response.getheaders.return_value = [("Set-Cookie", secret)]
                    with self.assertRaises(subprocess.CalledProcessError) as failure:
                        hello.run("ios", "http://127.0.0.1:5055", Path(temporary) / "credentials.json")
                    self.assertEqual(failure.exception.returncode, 65)
            files = list((Path(temporary) / "native-ci").glob("hello-diagnostics-*.json"))
            self.assertEqual(len(files), 1)
            text = files[0].read_text()
            self.assertNotIn(secret, text)
            evidence = json.loads(text)
            self.assertFalse(evidence["completed"])
            self.assertTrue(evidence["backendHealthAfterFailure"]["supportedVersion"])
            self.assertEqual([event["stage"] for event in evidence["events"]], [
                "proxy_received", "body_read_started", "body_read_finished", "upstream_send_started",
                "upstream_sent", "upstream_headers_received", "upstream_body_received", "proxy_forwarded"])
            self.assertEqual(evidence["events"][2]["bytesRead"], len(secret))

    def test_backend_health_failure_keeps_only_error_type(self):
        scenario = hello.Scenario("http://127.0.0.1:5055")
        with tempfile.TemporaryDirectory() as temporary, patch.dict(hello.os.environ, {"RUNNER_TEMP": temporary}):
            with patch.object(hello, "HTTPConnection") as backend:
                backend.return_value.getresponse.side_effect = TimeoutError("private-exception-text")
                scenario.retain_evidence("ios", False)
            text = next((Path(temporary) / "native-ci").glob("*.json")).read_text()
            self.assertNotIn("private-exception-text", text)
            self.assertEqual(json.loads(text)["backendHealthAfterFailure"]["errorType"], "TimeoutError")

    def test_success_does_not_probe_backend_and_events_are_bounded(self):
        scenario = hello.Scenario("http://127.0.0.1:5055")
        for _ in range(150):
            scenario.record("proxy_received", route="unrecognized")
        with tempfile.TemporaryDirectory() as temporary, patch.dict(hello.os.environ, {"RUNNER_TEMP": temporary}):
            with patch.object(hello, "HTTPConnection") as backend:
                scenario.retain_evidence("ios", True)
                backend.assert_not_called()
            evidence = json.loads(next((Path(temporary) / "native-ci").glob("*.json")).read_text())
            self.assertEqual(len(evidence["events"]), 128)
            self.assertTrue(evidence["eventsLimitReached"])
            self.assertIsNone(evidence["backendHealthAfterFailure"])

    def test_bad_pin_fails_before_downloading(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "api").mkdir()
            (root / "api/UPSTREAM_COMMIT").write_text("not-a-pin")
            with patch.object(sys.modules["local_seerr"], "ROOT", root):
                with patch("local_seerr.urlopen") as download, self.assertRaises(ValueError):
                    sys.modules["local_seerr"].source_tree()
                download.assert_not_called()

    def test_failed_download_does_not_poison_source_cache(self):
        import tempfile
        local = sys.modules["local_seerr"]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "api").mkdir()
            pin = "a" * 40
            (root / "api/UPSTREAM_COMMIT").write_text(pin + "\n# Fetched: 2026-09-05\n")
            with patch.object(local, "ROOT", root), patch.object(local, "urlopen", side_effect=OSError):
                with self.assertRaises(OSError):
                    local.source_tree()
            self.assertFalse((root / ".cache" / f"seerr-{pin}").exists())
            self.assertEqual([], list((root / ".cache").iterdir()))
