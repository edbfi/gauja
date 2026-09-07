#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Run root and feature smoke tests on the iOS 18 floor family and current SDK family."""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]


def select_runtimes(runtimes):
    versions = [runtime for runtime in runtimes if runtime.get("isAvailable") and runtime["name"].startswith("iOS ")]
    selected = []
    for major in (18, 26):
        candidates = [runtime for runtime in versions if runtime["version"].split(".")[0] == str(major)]
        if not candidates:
            raise ValueError(f"Install an iOS {major} simulator runtime before running smoke tests")
        selected.append(max(candidates, key=lambda value: tuple(map(int, value["version"].split(".")))))
    return selected


EXPECTED = {
    "Gauja": {"appDoesNotRegisterBackgroundWorkOrThirdPartyQueries", "testAddressValidation",
              "rootNavigatorKeepsItsBoundPathAndDoesNotPopPastRoot", "secretsSurviveReopeningAndWipeOnlyTheirProfile"},
    "Servers": {"invalidAddressDoesNotStartRequest"},
}


def validate_results(summary, tree, expected):
    if summary.get("totalTestCount") != len(expected) or summary.get("passedTests") != len(expected):
        raise ValueError("Missing or unexpected iOS smoke tests")
    if summary.get("failedTests") != 0 or summary.get("skippedTests") != 0:
        raise ValueError("iOS smoke tests failed or were skipped")
    cases = []

    def visit(node):
        if isinstance(node, dict):
            if node.get("nodeType") == "Test Case":
                cases.append(node)
            for value in node.values():
                visit(value)
        elif isinstance(node, list):
            for value in node:
                visit(value)

    visit(tree)
    names = {case.get("name", "").removesuffix("()").rsplit("/", 1)[-1] for case in cases}
    if len(cases) != len(expected) or names != expected or any(case.get("result") != "Passed" for case in cases):
        raise ValueError("Expected successful test bodies are absent from the xcresult test tree")


def stop(process):
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=5)


class Smoke:
    def __init__(self, evidence):
        self.evidence = evidence.resolve()
        self.deadline = time.monotonic() + 70 * 60
        self.sequence = 0
        self.failure = None
        self.devices = []

    def command(self, command, *, timeout=60, cwd=None, name=None, cleanup=False):
        self.sequence += 1
        path = self.evidence / (name or f"command-{self.sequence}.log")
        budget = timeout if cleanup else min(timeout, self.deadline - time.monotonic())
        if budget <= 0:
            raise TimeoutError("iOS smoke exceeded its 70-minute deadline")
        print(time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), " ".join(command), flush=True)
        with path.open("wb") as output:
            process = subprocess.Popen(command, cwd=cwd, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                process.wait(timeout=budget)
                if process.returncode:
                    raise subprocess.CalledProcessError(process.returncode, command)
            finally:
                stop(process)
        return path.read_text(errors="replace").strip()

    def results(self, bundle, scheme, *, validate):
        if not bundle.is_dir():
            raise ValueError("Missing result bundle: " + str(bundle))
        parsed = {}
        for kind in ("summary", "tests"):
            parsed[kind] = json.loads(self.command(
                ["xcrun", "xcresulttool", "get", "test-results", kind, "--path", str(bundle), "--compact"],
                name=bundle.stem + "-" + kind + ".json", cleanup=not validate))
        self.command(["xcrun", "xcresulttool", "export", "attachments", "--path", str(bundle),
                      "--output-path", str(self.evidence / (bundle.stem + "-attachments"))],
                     timeout=120, name=bundle.stem + "-attachments.log", cleanup=not validate)
        if validate:
            validate_results(parsed["summary"], parsed["tests"], EXPECTED[scheme])

    def test(self, identifier, runtime, scheme):
        bundle = self.evidence / (runtime["identifier"] + "-" + scheme + ".xcresult")
        command = ["xcodebuild"]
        if scheme == "Gauja":
            command += ["-project", "Gauja.xcodeproj", "-derivedDataPath", "DerivedData"]
        command += ["-scheme", scheme, "-destination", "platform=iOS Simulator,id=" + identifier,
                    "-resultBundlePath", str(bundle), "-skipPackagePluginValidation",
                    "-parallel-testing-enabled", "NO", "test"]
        if scheme == "Gauja":
            command += ["CODE_SIGNING_ALLOWED=NO"]
        cwd = ROOT / "apps/ios" if scheme == "Gauja" else ROOT / "apps/ios/Packages/Features/Servers"
        try:
            self.command(command, cwd=cwd, timeout=25 * 60, name=bundle.stem + "-xcodebuild.log")
        except Exception:
            try:
                self.results(bundle, scheme, validate=False)
            except Exception as error:
                print("Result collection failed:", error, flush=True)
            raise
        self.results(bundle, scheme, validate=True)

    def simulator(self, runtime, device):
        identifier = self.command(["xcrun", "simctl", "create", "Gauja smoke",
                                   "com.apple.CoreSimulator.SimDeviceType." + device, runtime["identifier"]])
        self.devices.append({"udid": identifier, "device": device, "runtime": runtime})
        (self.evidence / "simulators.json").write_text(json.dumps(self.devices, indent=2) + "\n")
        primary = None
        try:
            self.command(["xcrun", "simctl", "boot", identifier])
            self.command(["xcrun", "simctl", "bootstatus", identifier, "-b"], timeout=10 * 60)
            for scheme in ("Gauja", "Servers"):
                self.test(identifier, runtime, scheme)
        except Exception as error:
            primary = error
            raise
        finally:
            cleanup_error = None
            for operation in ("shutdown", "delete"):
                try:
                    self.command(["xcrun", "simctl", operation, identifier], cleanup=True)
                except Exception as error:
                    print("Simulator cleanup failed:", error, flush=True)
                    cleanup_error = cleanup_error or error
            if primary is None and cleanup_error is not None:
                raise cleanup_error

    def run(self):
        self.evidence.mkdir(parents=True, exist_ok=False)
        try:
            for name, command in {
                "xcode.txt": ["xcodebuild", "-version"],
                "swift.txt": ["swift", "--version"],
                "sdks.txt": ["xcodebuild", "-showsdks"],
            }.items():
                self.command(command, name=name)
            data = json.loads(self.command(["xcrun", "simctl", "list", "runtimes", "--json"], name="runtimes.json"))
            for runtime, device in zip(select_runtimes(data["runtimes"]), ["iPad-Pro-13-inch-M4-8GB", "iPhone-16-Pro"]):
                self.simulator(runtime, device)
        except Exception as error:
            self.failure = f"{type(error).__name__}: {error}"
            print("FAIL:", self.failure, flush=True)
        finally:
            (self.evidence / "status.json").write_text(json.dumps({"failure": self.failure}, indent=2) + "\n")
        return 1 if self.failure else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence-dir", type=Path, default=ROOT / ".cache/ci/ios-smoke")
    args = parser.parse_args()
    def interrupted(signum, frame):
        raise RuntimeError(f"Smoke interrupted by signal {signum}")
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    return Smoke(args.evidence_dir).run()


if __name__ == "__main__":
    raise SystemExit(main())
