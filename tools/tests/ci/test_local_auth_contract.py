# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("local_auth", Path(__file__).resolve().parents[2] / "ci/local-auth-contract.py")
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)


class LocalAuthTests(unittest.TestCase):
    def test_scrubbing_removes_nested_credentials_and_preserves_null(self):
        value = {"plexToken": "private", "settings": [{"apiKey": "operator", "password": "secret"}], "jellyfinAuthToken": None}
        safe = contract.scrub(value)
        self.assertEqual("REDACTED", safe["plexToken"])
        self.assertEqual("REDACTED", safe["settings"][0]["apiKey"])
        self.assertEqual("REDACTED", safe["settings"][0]["password"])
        self.assertIsNone(safe["jellyfinAuthToken"])
        self.assertEqual("private", value["plexToken"])

    def test_rejects_nonlocal_servers_before_sending_credentials(self):
        for base in ["https://external.invalid", "http://user:pass@localhost", "ftp://localhost", "http://localhost?q=secret"]:
            with self.assertRaises(ValueError):
                contract.run(base, {"email": "a", "password": "synthetic"})
