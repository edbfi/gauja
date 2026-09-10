# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("smoke", Path(__file__).resolve().parents[2] / "ci/ios-smoke.py")
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)

class SimulatorTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.runner = smoke.Smoke(self.directory)
        self.runtimes = [{"name": "iOS " + version, "version": version, "isAvailable": True,
                          "identifier": "runtime-" + version} for version in ("18.6", "26.5")]

    def test_missing_floor_is_a_failure(self):
        with self.assertRaises(ValueError):
            smoke.select_runtimes([{"name": "iOS 26.5", "version": "26.5", "isAvailable": True}])

    def test_available_floor_and_latest_sdk_family(self):
        data = [{"name": "iOS " + version, "version": version, "isAvailable": available}
                for version, available in [("18.0", True), ("18.6", True), ("26.5", True), ("26.6", False)]]
        self.assertEqual([v["version"] for v in smoke.select_runtimes(data)], ["18.6", "26.5"])

    def test_boot_failure_stops_testing_and_removes_simulator(self):
        def run(command, **kwargs):
            if "bootstatus" in command:
                raise smoke.subprocess.CalledProcessError(1, command)
            return "simulator-id"

        with patch.object(self.runner, "command", side_effect=run) as commands, \
                patch.object(self.runner, "test") as tests:
            with self.assertRaises(smoke.subprocess.CalledProcessError):
                self.runner.simulator(self.runtimes[0], "iPad-Pro-13-inch-M4-8GB")
        tests.assert_not_called()
        invocations = [call.args[0] for call in commands.call_args_list]
        self.assertFalse(any(command[0] == "xcodebuild" for command in invocations))
        self.assertEqual(invocations[-1], ["xcrun", "simctl", "delete", "simulator-id"])

    def test_four_serial_invocations_have_distinct_results(self):
        with patch.object(self.runner, "command") as command, patch.object(self.runner, "results") as results:
            for runtime in self.runtimes:
                for scheme in ("Gauja", "Servers"):
                    self.runner.test("device-id", runtime, scheme)
        paths = []
        for call in command.call_args_list:
            args = call.args[0]
            paths.append(args[args.index("-resultBundlePath") + 1])
            self.assertEqual(args[args.index("-parallel-testing-enabled") + 1], "NO")
            self.assertEqual(call.kwargs["timeout"], 25 * 60)
        self.assertEqual(len(set(paths)), 4)
        self.assertEqual(results.call_count, 4)
        self.assertTrue(all(call.kwargs["validate"] for call in results.call_args_list))

    def test_test_failure_preserves_results_and_primary_error(self):
        with patch.object(self.runner, "command", side_effect=TimeoutError("test timed out")), \
                patch.object(self.runner, "results", side_effect=ValueError("partial bundle")) as results:
            with self.assertRaisesRegex(TimeoutError, "test timed out"):
                self.runner.test("device", self.runtimes[0], "Gauja")
        self.assertFalse(results.call_args.kwargs["validate"])

    def test_cleanup_failure_does_not_replace_test_failure(self):
        def command(args, **kwargs):
            if args[2] in ("shutdown", "delete"):
                raise RuntimeError("cleanup failed")
            return "simulator-id"
        with patch.object(self.runner, "command", side_effect=command) as commands, \
                patch.object(self.runner, "test", side_effect=TimeoutError("primary test failure")):
            with self.assertRaisesRegex(TimeoutError, "primary test failure"):
                self.runner.simulator(self.runtimes[0], "iPad-Pro-13-inch-M4-8GB")
        self.assertEqual(commands.call_args.args[0][2], "delete")

    def test_hanging_process_is_bounded(self):
        with self.assertRaises(smoke.subprocess.TimeoutExpired):
            self.runner.command([sys.executable, "-c", "import time; time.sleep(60)"], timeout=0.1)

    def test_missing_result_bundle_fails(self):
        with self.assertRaisesRegex(ValueError, "Missing result bundle"):
            self.runner.results(self.directory / "absent.xcresult", "Gauja", validate=True)

    def test_result_validation_requires_expected_bodies(self):
        expected = smoke.EXPECTED["Gauja"]
        summary = {"totalTestCount": len(expected), "passedTests": len(expected), "failedTests": 0, "skippedTests": 0}
        tree = {"testNodes": [{"nodeType": "Test Suite", "children": [
            {"nodeType": "Test Case", "name": name + "()", "result": "Passed"} for name in expected]}]}
        smoke.validate_results(summary, tree, expected)
        for key, value in (("totalTestCount", 0), ("passedTests", 0), ("failedTests", 1), ("skippedTests", 1)):
            with self.subTest(key=key), self.assertRaises(ValueError):
                smoke.validate_results(summary | {key: value}, tree, expected)
        for replacement in ({}, {"testNodes": [{"nodeType": "Test Case", "name": "other", "result": "Passed"}]}):
            with self.assertRaises(ValueError):
                smoke.validate_results(summary, replacement, expected)
        for case in tree["testNodes"][0]["children"]:
            case["result"] = "Skipped"
        with self.assertRaises(ValueError):
            smoke.validate_results(summary, tree, expected)

if __name__ == "__main__":
    unittest.main()
