# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
import hashlib
from pathlib import Path
import socket
import ssl
import sys
import unittest
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "ci"))
from tls_server import tls_server
sys.path.pop(0)


class TLSFixtureTests(unittest.TestCase):
    def test_fixture_is_self_signed_and_reports_actual_leaf_fingerprint(self):
        with tls_server() as (environment, paths):
            base = environment["GAUJA_TLS_SERVER"]
            with self.assertRaises(URLError):
                urlopen(base + "/tls", timeout=5)
            address = urlsplit(base)
            # Only this fixture test bypasses trust to inspect its ephemeral certificate.
            context = ssl._create_unverified_context()
            with socket.create_connection((address.hostname, address.port), timeout=5) as connection:
                with context.wrap_socket(connection, server_hostname="localhost") as secured:
                    actual = hashlib.sha256(secured.getpeercert(binary_form=True)).hexdigest()
            self.assertEqual(environment["GAUJA_TLS_FINGERPRINT"], actual)
            self.assertEqual([], paths)
            with urlopen(base + "/tls", context=context, timeout=5) as response:
                self.assertEqual(b"trusted", response.read())
            self.assertEqual(["/tls"], paths)
