# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "contract"))
from responses import OpenAPIResponseValidator, validate_response


class ResponseTests(unittest.TestCase):
    def test_null_requires_explicit_nullable(self):
        schema = {"type": "object", "properties": {"name": {"type": "string", "nullable": True}, "id": {"type": "integer"}}}
        validator = OpenAPIResponseValidator(schema)
        self.assertFalse(list(validator.iter_errors({"name": None, "id": 1})))
        self.assertTrue(list(validator.iter_errors({"name": None, "id": None})))

    def test_invalid_response_does_not_print_payload(self):
        spec = {"paths": {"/auth/local": {"post": {"responses": {"200": {"content": {"application/json": {"schema": {"type": "integer"}}}}}}}}}
        with self.assertRaises(ValueError) as raised:
            validate_response(spec, "/auth/local", "POST", 200, "private-value")
        self.assertNotIn("private-value", str(raised.exception))
