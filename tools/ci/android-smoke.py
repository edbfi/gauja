#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Own one Android emulator, retain evidence, and require healthy real Hilt tests."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "apps/android"
EXPECTED_TESTS = {
    ("app.gauja.ServerCheckTest", "injectedProbeRendersDomainResult"),
    ("app.gauja.persistence.SecretStoreTest", "keystoreBackedSecretsRemainEncryptedAndProfileIsolated"),
}
NAMESPACE = "{http://schemas.android.com/apk/res/android}"
CRITICAL = re.compile(r"hasReadColorBufferDma|Transport endpoint is not connected|"
                      r"Fatal signal[^\n]*surfaceflinger|>>> /system/bin/surfaceflinger <<<|"
                      r"Service 'surfaceflinger'[^\n]*(?:exited|killed|received signal)|"
                      r"FATAL EXCEPTION IN SYSTEM PROCESS", re.I)


def prepare_avd_target(path, api):
    """Repair older AVD Manager's API 37.0 -> android-0 metadata conversion."""
    content = path.read_text()
    targets = re.findall(r"^target=(.+)$", content, re.M)
    if api == "37.0" and targets == ["android-0"]:
        content = re.sub(r"^target=android-0$", "target=android-37", content, flags=re.M)
        path.write_text(content)
        return True
    if len(targets) != 1 or targets[0] not in {"android-" + api, "android-" + api.split(".")[0]}:
        raise ValueError("AVD target does not match the selected system image: " + repr(targets))
    return False


def validate_results(directory):
    files = sorted(directory.rglob("TEST-*.xml"))
    if not files:
        raise ValueError("Missing Android JUnit results")
    cases = []
    for path in files:
        root = ET.parse(path).getroot()
        suites = [node for node in root.iter() if node.tag in ("testsuite", "testsuites")]
        for suite in suites:
            if int(suite.get("tests", "-1")) != len(list(suite.iter("testcase"))):
                raise ValueError("JUnit suite count does not match its test cases")
        if any(int(suite.get(key, "0")) for suite in suites
               for key in ("failures", "errors", "skipped")):
            raise ValueError("Android suite reports failures, errors or skips")
        cases.extend(root.iter("testcase"))
    if len(cases) != len(EXPECTED_TESTS) or {(case.get("classname"), case.get("name")) for case in cases} != EXPECTED_TESTS:
        raise ValueError("Expected exactly the Hilt screen and real Keystore instrumentation tests")
    if any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
        raise ValueError("Hilt instrumentation did not pass")


def validate_manifests(app, test):
    app, test = ET.fromstring(app), ET.fromstring(test)
    instrumentation = test.find("instrumentation")
    if app.get("package") != "app.gauja" or test.get("package") != "app.gauja.test":
        raise ValueError("Unexpected APK package")
    if instrumentation is None or instrumentation.get(NAMESPACE + "targetPackage") != "app.gauja":
        raise ValueError("Wrong or missing instrumentation targetPackage")
    if instrumentation.get(NAMESPACE + "name") != "app.gauja.HiltTestRunner":
        raise ValueError("Unexpected instrumentation runner")
    activities = app.findall("application/activity")
    if not any(activity.get(NAMESPACE + "name") in (".MainActivity", "app.gauja.MainActivity")
               and activity.get(NAMESPACE + "exported") == "true" for activity in activities):
        raise ValueError("Missing exported MainActivity")


def stop(process):
    """Stop only a process group created by this helper, including hung children."""
    if process is not None and process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=5)


class Smoke:
    def __init__(self, args):
        self.args = args
        self.evidence = args.evidence_dir.resolve()
        self.deadline = time.monotonic() + 25 * 60
        self.emulator = None
        self.logcat = None
        self.identities = None
        self.stage = "provisioned"
        self.offsets = {}
        self.failure = None
        self.sentinel = "gauja-smoke-" + args.avd
        self.events = []

    def record(self, message):
        entry = {"host_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                 "host_monotonic": time.monotonic(), "stage": self.stage, "message": message}
        self.events.append(entry)
        with (self.evidence / "events.jsonl").open("a") as output:
            output.write(json.dumps(entry) + "\n")
        print(message, flush=True)

    def command(self, command, *, timeout=15, output=None, monitor=False, watch_boot=False):
        remaining = min(timeout, self.deadline - time.monotonic())
        if remaining <= 0:
            raise TimeoutError("Android smoke deadline exhausted")
        self.record("Run: " + " ".join(map(str, command)))
        path = self.evidence / (output or "command.txt")
        with path.open("wb") as destination:
            process = subprocess.Popen(command, stdout=destination, stderr=subprocess.STDOUT,
                                       start_new_session=True, cwd=ROOT)
            try:
                end = time.monotonic() + remaining
                while process.poll() is None:
                    if time.monotonic() >= end:
                        raise TimeoutError("Command timed out: " + str(command[0]))
                    if monitor:
                        self.health()
                    elif watch_boot:
                        self.crashes()
                    time.sleep(min(2 if monitor else 0.1, max(0, end - time.monotonic())))
                if process.returncode:
                    raise subprocess.CalledProcessError(process.returncode, command,
                                                        path.read_text(errors="replace"))
            finally:
                stop(process)
        content = path.read_bytes()
        if CRITICAL.search(content.decode(errors="replace")):
            raise RuntimeError("Critical guest error in " + path.name)
        return content.decode(errors="replace").strip()

    def adb(self, *command, **kwargs):
        return self.command(["adb", "-s", self.args.serial, *command], **kwargs)

    def crashes(self):
        for name in ("emulator.log", "logcat.log"):
            path = self.evidence / name
            if not path.exists():
                continue
            with path.open("rb") as source:
                source.seek(max(0, self.offsets.get(name, 0) - 512))
                content = source.read()
                self.offsets[name] = source.tell()
            if CRITICAL.search(content.decode(errors="replace")):
                raise RuntimeError("Critical guest crash/storage error in " + name)
        if self.emulator is not None and self.emulator.poll() is not None:
            raise RuntimeError("Emulator exited unexpectedly")
        if self.logcat is not None and self.logcat.poll() is not None:
            raise RuntimeError("Guest logcat stream exited unexpectedly")

    def health(self):
        self.crashes()
        identities = tuple(self.adb("shell", "pidof", name) for name in ("surfaceflinger", "system_server"))
        if not all(re.fullmatch(r"\d+", value) for value in identities):
            raise RuntimeError("Missing compositor or system server")
        if self.identities is not None and identities != self.identities:
            raise RuntimeError("SurfaceFlinger/system_server restarted")
        self.identities = identities
        for service in ("SurfaceFlinger", "package", "activity", "mount"):
            if self.adb("shell", "service", "check", service) != f"Service {service}: found":
                raise RuntimeError("Missing service: " + service)
        self.adb("shell", "cmd", "package", "list", "packages")
        if not self.adb("shell", "cmd", "activity", "get-current-user").isdigit():
            raise RuntimeError("Activity manager is unresponsive")
        # Check the failing mount itself as well as an ordinary shell-writable external directory.
        self.adb("shell", "ls -ld /sdcard/Android /sdcard")
        for directory in ("/data/local/tmp", "/sdcard"):
            path = directory + "/" + self.sentinel
            result = self.adb("shell", f"printf gauja > {path} && cat {path} && rm {path}")
            if result != "gauja":
                raise RuntimeError("Storage round trip failed: " + directory)
        self.crashes()

    def snapshot(self, prefix):
        for name, command in {
            "clock": ["shell", "date -u; cat /proc/uptime; cat /proc/meminfo; getconf PAGE_SIZE"],
            "properties": ["shell", "getprop"],
            "services": ["shell", "service", "list"],
            "processes": ["shell", "ps", "-A"],
            "storage": ["shell", "df -h; mount"],
            "packages": ["shell", "dumpsys", "package", "app.gauja"],
        }.items():
            try:
                self.adb(*command, output=prefix + "-" + name + ".txt")
            except Exception as error:
                self.record(f"Snapshot {name} unavailable: {error}")

    def boot(self):
        image = Path(os.environ["ANDROID_HOME"]) / "system-images" / ("android-" + self.args.api) / "google_apis/x86_64"
        properties = (image / "source.properties").read_text()
        if not re.search(r"^Pkg.Revision=" + re.escape(self.args.image_revision) + r"$", properties, re.M):
            raise ValueError("System image revision changed; review the pairing")
        if not re.search(r"^AndroidVersion.ApiLevel=" + re.escape(self.args.api) + r"$", properties, re.M):
            raise ValueError("SDK image API metadata does not match the selected API")
        (self.evidence / "image.properties").write_text(properties)
        avd_home = Path(os.environ["ANDROID_AVD_HOME"]).resolve()
        config = avd_home / (self.args.avd + ".avd/config.ini")
        shutil.copy2(config, self.evidence / "avd.ini")
        root_ini = avd_home / (self.args.avd + ".ini")
        shutil.copy2(root_ini, self.evidence / "root-avd-before.ini")
        if prepare_avd_target(root_ini, self.args.api):
            self.record("Corrected AVD Manager's android-0 target to android-37; system image unchanged")
        shutil.copy2(root_ini, self.evidence / "root-avd.ini")
        with (self.evidence / "emulator-binaries.sha256").open("w") as output:
            for executable in (self.args.emulator, self.args.emulator.parent / "qemu/linux-x86_64/qemu-system-x86_64-headless"):
                with executable.open("rb") as source:
                    output.write(hashlib.file_digest(source, "sha256").hexdigest() + "  " + str(executable) + "\n")
        self.command([str(self.args.emulator), "-no-window", "-version"], output="emulator-version.txt")
        self.command([str(self.args.emulator), "-accel-check"], output="acceleration.txt")
        self.command(["adb", "start-server"])
        port = self.args.serial.removeprefix("emulator-")
        command = [str(self.args.emulator), "-avd", self.args.avd, "-port", port,
                   "-no-window", "-no-audio", "-no-snapshot", "-no-metrics", "-no-boot-anim",
                   "-memory", str(self.args.memory_mib), "-cores", "2", "-show-kernel", "-gpu", "software",
                   "-verbose", "-debug", "init,gles,avd_config,ini,time"]
        self.record("Emulator: " + " ".join(command))
        with (self.evidence / "emulator.log").open("wb") as output:
            self.emulator = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        self.stage = "boot"
        self.adb("wait-for-device", timeout=180, output="wait-for-device.txt", watch_boot=True)
        with (self.evidence / "logcat.log").open("wb") as output:
            self.logcat = subprocess.Popen(["adb", "-s", self.args.serial, "logcat", "-b", "all", "-v", "epoch"],
                                          stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        boot_deadline = min(self.deadline, time.monotonic() + 180)
        while time.monotonic() < boot_deadline:
            self.crashes()
            if self.adb("shell", "getprop", "sys.boot_completed") == "1":
                break
            time.sleep(2)
        else:
            raise TimeoutError("Guest boot did not complete within 180 seconds")
        api = self.adb("shell", "getprop", "ro.build.version.sdk")
        abi = self.adb("shell", "getprop", "ro.product.cpu.abi")
        if api != self.args.api.split(".")[0] or abi != "x86_64":
            raise ValueError("Unexpected guest API/ABI: " + api + "/" + abi)
        fingerprint = self.adb("shell", "getprop", "ro.build.fingerprint", output="fingerprint.txt")
        if self.args.api == "37.0" and fingerprint != "google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys":
            raise ValueError("API 37 guest fingerprint changed")
        page_size = self.adb("shell", "getconf", "PAGE_SIZE", output="page-size.txt")
        if not page_size.isdigit() or int(page_size) <= 0:
            raise ValueError("Could not measure guest page size")
        trace = (self.evidence / "emulator.log").read_text(errors="replace")
        parsed_api = re.search(r"API level: (\d+)", trace)
        if parsed_api is None or parsed_api[1] != api:
            raise ValueError("Emulator's parsed API does not match the running guest")
        memory = self.adb("shell", "cat", "/proc/meminfo", output="memory.txt")
        total = re.search(r"^MemTotal:\s+(\d+) kB", memory, re.M)
        if total is None or int(total[1]) < (self.args.memory_mib - 512) * 1024:
            raise ValueError("Effective guest RAM is below the requested allocation, allowing for reserved memory")
        (self.evidence / "guest-identity.json").write_text(json.dumps(
            {"requested_api": self.args.api, "parsed_api": parsed_api[1], "guest_api": api, "abi": abi,
             "fingerprint": fingerprint, "page_size": int(page_size), "mem_total_kib": int(total[1])}, indent=2) + "\n")
        self.snapshot("boot")
        self.stage = "readiness"
        start = time.monotonic()
        deadline = self.deadline
        self.deadline = min(deadline, start + 120)
        try:
            while time.monotonic() - start < 60:
                self.health()
                time.sleep(2)
        finally:
            self.deadline = deadline
        launcher = self.adb("shell", "cmd", "package", "resolve-activity", "--brief", "-a",
                            "android.intent.action.MAIN", "-c", "android.intent.category.HOME", output="home-launcher.txt")
        if "/" not in launcher or "No activity" in launcher:
            raise RuntimeError("System launcher lookup failed")
        self.adb("exec-out", "screencap", "-p", timeout=30, output="boot.png")
        if not (self.evidence / "boot.png").read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Screenshot readback did not produce a PNG")
        self.health()

    def tests(self):
        self.stage = "assemble"
        gradle = [str(ANDROID / "gradlew"), "--project-dir", str(ANDROID)]
        self.command(gradle + ["assembleDebug", "assembleDebugAndroidTest"], timeout=25 * 60,
                     output="assemble.log", monitor=True)
        apk_dir = ANDROID / "app/build/outputs/apk"
        apks = [apk_dir / "debug/app-x86_64-debug.apk", apk_dir / "androidTest/debug/app-debug-androidTest.apk"]
        manifests = []
        for index, apk in enumerate(apks):
            if not apk.is_file():
                raise ValueError("Missing APK: " + str(apk))
            shutil.copy2(apk, self.evidence / apk.name)
            (self.evidence / (apk.name + ".sha256")).write_text(hashlib.sha256(apk.read_bytes()).hexdigest() + "\n")
            manifests.append(self.command(["apkanalyzer", "manifest", "print", str(apk)], timeout=60,
                                          output=("app" if index == 0 else "test") + "-manifest.xml"))
        validate_manifests(*manifests)
        self.stage = "install"
        for apk in apks:
            self.adb("install", "-r", "-t", str(apk), timeout=120, output=apk.name + "-install.txt")
        for package in ("app.gauja", "app.gauja.test"):
            if not self.adb("shell", "pm", "path", package, output=package + "-path.txt").startswith("package:"):
                raise RuntimeError("Installed package missing: " + package)
        instrumentation = self.adb("shell", "pm", "list", "instrumentation", output="instrumentation.txt")
        if "app.gauja.test/app.gauja.HiltTestRunner (target=app.gauja)" not in instrumentation:
            raise RuntimeError("Installed instrumentation target does not match APK")
        launcher = self.adb("shell", "cmd", "package", "resolve-activity", "--brief", "-a",
                            "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-p", "app.gauja",
                            output="app-launcher.txt")
        if not re.search(r"app\.gauja/(?:app\.gauja\.)?\.?MainActivity", launcher):
            raise RuntimeError("Installed Gauja launcher lookup failed on a healthy guest")
        self.snapshot("pre-test")
        results = ANDROID / "app/build/outputs/androidTest-results/connected"
        reports = ANDROID / "app/build/reports/androidTests/connected"
        for directory in (results, reports):
            if directory.exists():
                shutil.rmtree(directory)
        self.stage = "instrumentation"
        self.command(gradle + ["connectedDebugAndroidTest"], timeout=25 * 60,
                     output="instrumentation.log", monitor=True)
        validate_results(results)
        self.stage = "post-test"
        end = time.monotonic() + 30
        while time.monotonic() < end:
            self.health()
            time.sleep(2)
        self.crashes()
        self.record("PASS: real Hilt test and activity recreation, with sustained guest health")

    def collect(self):
        if self.emulator is None:
            self.record("Guest evidence unavailable: emulator startup was not reached")
            return
        self.deadline = time.monotonic() + 180
        self.snapshot("final")
        for name, command, timeout in [
            ("crash.log", ["logcat", "-d", "-b", "crash"], 15),
            ("final.png", ["exec-out", "screencap", "-p"], 15),
            ("bugreport.zip", ["bugreport", str(self.evidence / "bugreport.zip")], 120),
        ]:
            if name == "bugreport.zip" and self.failure is None:
                continue
            try:
                self.adb(*command, timeout=timeout, output=name + ".log" if name == "bugreport.zip" else name)
            except Exception as error:
                self.record(f"Collection {name}: {error}")
        for source, name in [(ANDROID / "app/build/outputs/androidTest-results/connected", "junit"),
                             (ANDROID / "app/build/reports/androidTests/connected", "html")]:
            if self.failure is None and (not source.is_dir() or not any(source.rglob("*.xml" if name == "junit" else "index.html"))):
                raise ValueError("Successful instrumentation is missing its " + name + " report")
            if source.exists():
                shutil.copytree(source, self.evidence / name)
        # Preserve raw traces; absence of a printed feature value is not an enabled/disabled result.
        lines = (self.evidence / "emulator.log").read_text(errors="replace").splitlines() if self.emulator else []
        (self.evidence / "graphics-features.txt").write_text(
            "Requested GPU: software. Effective fields absent from trace: unavailable.\n" +
            "\n".join(line for line in lines if re.search(r"renderer|backend|feature|GLDirectMem|SharedSlots|ReadColorBuffer|RAM|api level", line, re.I)) + "\n")

    def cleanup(self):
        stop(self.logcat)
        stop(self.emulator)
        avd_home = Path(os.environ["ANDROID_AVD_HOME"]).resolve()
        for path in (avd_home / (self.args.avd + ".avd"), avd_home / (self.args.avd + ".ini")):
            if path.is_dir():
                shutil.rmtree(path)
            elif path.exists():
                path.unlink()

    def run(self):
        self.evidence.mkdir(parents=True, exist_ok=False)
        try:
            self.boot()
            self.tests()
        except Exception as error:
            self.failure = f"{type(error).__name__}: {error}"
            self.record("FAIL: " + self.failure)
        finally:
            for name, operation in (("collect", self.collect), ("final crash check", self.crashes), ("cleanup", self.cleanup)):
                try:
                    operation()
                except Exception as error:
                    self.record(name + " failed: " + str(error))
                    if self.failure is None:
                        self.failure = str(error)
            (self.evidence / "status.json").write_text(json.dumps(
                {"stage": self.stage, "failure": self.failure, "serial": self.args.serial,
                 "api": self.args.api, "memory_mib": self.args.memory_mib}, indent=2) + "\n")
        return 1 if self.failure else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emulator", type=Path, required=True)
    parser.add_argument("--avd", required=True)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--api", choices=("30", "37.0"), required=True)
    parser.add_argument("--image-revision", required=True)
    parser.add_argument("--memory-mib", type=int, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"gauja-[a-zA-Z0-9-]+", args.avd) or not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Use a task-owned gauja-* AVD and an explicit emulator serial")
    args.emulator = args.emulator.resolve()
    os.environ["ANDROID_SERIAL"] = args.serial
    def interrupted(signum, frame):
        raise RuntimeError(f"Smoke interrupted by signal {signum}")
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    return Smoke(args).run()


if __name__ == "__main__":
    raise SystemExit(main())
