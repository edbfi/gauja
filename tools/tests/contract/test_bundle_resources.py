# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("bundle_resources", ROOT / "tools/contract/bundle-resources.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BundleTests(unittest.TestCase):
    def test_missing_and_modified_copy_fail_without_rewriting(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "api").mkdir()
            (root / "api/compat.json").write_bytes(b"{}\n")
            with self.assertRaises(ValueError):
                module.bundle(root, True)
            module.bundle(root, False)
            module.bundle(root, True)
            target = root / module.DESTINATIONS[0]
            target.write_bytes(b"changed")
            with self.assertRaises(ValueError):
                module.bundle(root, True)
            self.assertEqual(b"changed", target.read_bytes())
