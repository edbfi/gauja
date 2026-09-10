# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
import importlib.util
from pathlib import Path
import tempfile
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "ci"))
spec = importlib.util.spec_from_file_location("egress", Path(__file__).resolve().parents[2] / "ci/egress_test.py")
egress = importlib.util.module_from_spec(spec)
spec.loader.exec_module(egress)
sys.path.pop(0)

class EgressTests(unittest.TestCase):
    def test_missing_skipped_and_failing_tests_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.xml"
            for counts in ['tests="0"', 'tests="2" skipped="1"', 'tests="2" failures="1"']:
                path.write_text('<testsuite ' + counts + '/>')
                with self.assertRaises(ValueError):
                    egress.assert_android_results(path)
            path.write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
            egress.assert_android_results(path)

if __name__ == "__main__":
    unittest.main()
