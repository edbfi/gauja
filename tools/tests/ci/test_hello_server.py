# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
import importlib.util
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
