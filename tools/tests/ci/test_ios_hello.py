# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("ios_hello", Path(__file__).resolve().parents[2] / "ci/ios_hello.py")
hello = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hello)


class IOSHelloTests(unittest.TestCase):
    def test_missing_unavailable_or_wrong_platform_runtimes_fail(self):
        for values in [[], [{"isAvailable": False, "identifier": "com.apple.CoreSimulator.SimRuntime.iOS-26", "version": "26.0"}],
                       [{"isAvailable": True, "identifier": "com.apple.CoreSimulator.SimRuntime.tvOS-26", "version": "26.0"}]]:
            with self.assertRaises(ValueError):
                hello.runtime_identifier(values)

    def test_latest_available_ios26_is_selected(self):
        values = [{"isAvailable": True, "identifier": "com.apple.CoreSimulator.SimRuntime.iOS-" + version,
                   "version": version} for version in ("26.1", "26.6", "18.6")]
        self.assertTrue(hello.runtime_identifier(values).endswith("26.6"))

    def test_environment_is_injected_only_into_test_targets(self):
        value = {"TestConfigurations": [{"TestTargets": [{"TestBundlePath": "test.xctest", "EnvironmentVariables": {"existing": "value"}}]}],
                 "metadata": {"EnvironmentVariables": {"unchanged": "yes"}}}
        self.assertEqual(1, hello.inject_environment(value, {"GAUJA_AUTH_SERVER": "http://localhost:1234"}))
        target = value["TestConfigurations"][0]["TestTargets"][0]
        self.assertEqual("value", target["EnvironmentVariables"]["existing"])
        self.assertEqual("http://localhost:1234", target["EnvironmentVariables"]["GAUJA_AUTH_SERVER"])
        self.assertEqual({"unchanged": "yes"}, value["metadata"]["EnvironmentVariables"])
        self.assertEqual(0, hello.inject_environment({}, {"test": "value"}))
