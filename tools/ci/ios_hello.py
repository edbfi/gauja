# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Run the app-hosted Swift Testing acceptance suite on an isolated iOS simulator."""
import json
import os
from pathlib import Path
import plistlib
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]


def runtime_identifier(runtimes):
    available = [runtime for runtime in runtimes if runtime.get("isAvailable") and
                 runtime.get("identifier", "").startswith("com.apple.CoreSimulator.SimRuntime.iOS-") and
                 runtime.get("version", "").split(".")[0] == "26"]
    if not available:
        raise ValueError("An available iOS 26 simulator runtime is required")
    return max(available, key=lambda item: tuple(int(part) for part in item["version"].split(".")))["identifier"]


def inject_environment(value, environment):
    count = 0
    if isinstance(value, dict):
        if "TestBundlePath" in value:
            value.setdefault("EnvironmentVariables", {}).update(environment)
            count += 1
        for child in value.values():
            count += inject_environment(child, environment)
    elif isinstance(value, list):
        for child in value:
            count += inject_environment(child, environment)
    return count


def run(environment):
    ios = ROOT / "apps/ios"
    subprocess.run(["xcodegen", "generate"], cwd=ios, check=True)
    runtimes = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "runtimes", "--json"]))["runtimes"]
    runtime = runtime_identifier(runtimes)
    identifier = subprocess.check_output(["xcrun", "simctl", "create", "gauja-hello-" + uuid.uuid4().hex,
                                          "com.apple.CoreSimulator.SimDeviceType.iPhone-17", runtime], text=True).strip()
    output = None
    try:
        subprocess.run(["xcrun", "simctl", "boot", identifier], check=True)
        subprocess.run(["xcrun", "simctl", "bootstatus", identifier, "-b"], check=True, timeout=180)
        destination = "platform=iOS Simulator,id=" + identifier
        subprocess.run(["xcodebuild", "-project", "Gauja.xcodeproj", "-scheme", "Gauja", "-destination", destination,
                        "-derivedDataPath", "DerivedData", "-skipPackagePluginValidation", "build-for-testing",
                        "CODE_SIGNING_ALLOWED=NO"], cwd=ios, check=True)
        products = ios / "DerivedData/Build/Products"
        candidates = [file for file in products.glob("*.xctestrun") if not file.name.startswith("GaujaHello-")]
        if not candidates:
            raise ValueError("Xcode did not produce an app-hosted test configuration")
        source = max(candidates, key=lambda file: file.stat().st_mtime_ns)
        configuration = plistlib.loads(source.read_bytes())
        if not inject_environment(configuration, {key: environment[key] for key in ("GAUJA_AUTH_SERVER", "GAUJA_AUTH_CREDENTIALS")}):
            raise ValueError("Xcode test configuration contains no test targets")
        output = products / ("GaujaHello-" + uuid.uuid4().hex + ".xctestrun")
        output.write_bytes(plistlib.dumps(configuration))
        output.chmod(0o600)
        evidence = Path(os.environ.get("RUNNER_TEMP", ROOT / ".cache")) / "native-ci" / ("hello-" + uuid.uuid4().hex + ".xcresult")
        evidence.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["xcodebuild", "test-without-building", "-xctestrun", str(output), "-destination", destination,
                        "-only-testing:GaujaTests/HelloServerTests", "-resultBundlePath", str(evidence)], cwd=ios, check=True)
    finally:
        if output is not None:
            output.unlink(missing_ok=True)
        subprocess.run(["xcrun", "simctl", "shutdown", identifier], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(["xcrun", "simctl", "delete", identifier], check=True)
