#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Disposable pinned Seerr bootstrap for native acceptance tests; never shipped in either app."""
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import secrets
import socket
import subprocess
import tarfile
import tempfile
import time
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[2]


def source_tree():
    pin = (ROOT / "api/UPSTREAM_COMMIT").read_text().strip()
    if not re.fullmatch(r"[a-f0-9]{40}", pin):
        raise ValueError("Invalid upstream pin")
    directory = ROOT / ".cache" / f"seerr-{pin}"
    if not directory.exists():
        directory.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=directory.parent, prefix="seerr-download-") as staging, tempfile.TemporaryFile() as archive:
            with urlopen(f"https://api.github.com/repos/seerr-team/seerr/tarball/{pin}", timeout=60) as response:
                archive.write(response.read())
            archive.seek(0)
            with tarfile.open(fileobj=archive) as tar:
                members = tar.getmembers()
                prefix = members[0].name.split("/")[0] + "/"
                for member in members:
                    if member.name.startswith(prefix):
                        member.name = member.name[len(prefix):]
                        if member.name:
                            tar.extract(member, staging, filter="data")
            Path(staging).rename(directory)
    return pin, directory


def wait_ready(base, process=None):
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        if process is not None and process.poll() is not None:
            raise RuntimeError("Local Seerr exited during startup")
        try:
            with urlopen(base + "/api/v1/status?checkUpdateAvailable=false", timeout=2) as response:
                if json.load(response).get("version") == "3.4.1":
                    return
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise TimeoutError("Local Seerr did not become ready")


def node_environment(source):
    version = re.search(r"FROM node:(\d+\.\d+\.\d+)-", (source / "Dockerfile").read_text()).group(1)
    architecture = "arm64" if platform.machine() == "arm64" else "x64"
    name = f"node-v{version}-darwin-{architecture}"
    directory = ROOT / ".cache" / name
    if not directory.exists():
        filename = name + ".tar.xz"
        base = f"https://nodejs.org/dist/v{version}/"
        with urlopen(base + "SHASUMS256.txt", timeout=60) as response:
            checksums = dict(line.split(maxsplit=1)[::-1] for line in response.read().decode().splitlines())
        with urlopen(base + filename, timeout=60) as response:
            archive = response.read()
        if hashlib.sha256(archive).hexdigest() != checksums.get(filename):
            raise ValueError("Node archive checksum mismatch")
        with tempfile.TemporaryFile() as file:
            file.write(archive)
            file.seek(0)
            with tarfile.open(fileobj=file) as tar:
                tar.extractall(ROOT / ".cache", filter="data")
    env = {key: os.environ[key] for key in ("HOME", "TMPDIR", "LANG") if key in os.environ}
    env.update(PATH=f"{directory / 'bin'}:{os.environ['PATH']}", CI="true", NEXT_TELEMETRY_DISABLED="1", CYPRESS_INSTALL_BINARY="0")
    subprocess.run([str(directory / "bin/corepack"), "enable", "--install-directory", str(directory / "bin")], env=env, check=True)
    return env


@contextmanager
def initialized_server():
    pin, source = source_tree()
    with tempfile.TemporaryDirectory(prefix="gauja-seerr-") as temporary:
        directory = Path(temporary)
        credentials = directory / "credentials.json"
        credentials.write_text(json.dumps({"email": "admin@example.invalid", "password": secrets.token_urlsafe(32)}))
        credentials.chmod(0o600)
        with (directory / "server.log").open("w") as log:
            if platform.system() == "Linux":
                image = f"gauja-contract-{pin}"
                found = subprocess.run(["docker", "image", "inspect", image], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                if found.returncode:
                    subprocess.run(["docker", "build", "--build-arg", f"COMMIT_TAG={pin}", "-t", image, str(source)], stdout=log, stderr=log, check=True)
                name = "gauja-hello-" + secrets.token_hex(6)
                subprocess.run(["docker", "run", "-d", "--name", name, "-p", "127.0.0.1::5055", "-e", "LOG_LEVEL=error",
                                "-v", f"{ROOT / 'tools/ci/seed-local-auth.cjs'}:/tmp/gauja-seed.cjs:ro", image], stdout=log, stderr=log, check=True)
                try:
                    binding = subprocess.check_output(["docker", "port", name, "5055"], text=True).strip()
                    base = "http://" + binding
                    wait_ready(base)
                    subprocess.run(["docker", "exec", "-i", name, "node", "/tmp/gauja-seed.cjs"], input=credentials.read_text(), text=True, stdout=log, stderr=log, check=True)
                    yield base, credentials
                finally:
                    subprocess.run(["docker", "rm", "-f", name], stdout=log, stderr=log, check=True)
            elif platform.system() == "Darwin":
                env = node_environment(source)
                env["COMMIT_TAG"] = pin
                if not (source / "dist/index.js").exists():
                    subprocess.run(["pnpm", "install", "--frozen-lockfile"], cwd=source, env=env, stdout=log, stderr=log, check=True)
                    subprocess.run(["pnpm", "build"], cwd=source, env=env, stdout=log, stderr=log, check=True)
                (source / "committag.json").write_text(json.dumps({"commitTag": pin}))
                with socket.socket() as reservation:
                    reservation.bind(("127.0.0.1", 0))
                    port = reservation.getsockname()[1]
                env.update(CONFIG_DIRECTORY=str(directory / "config"), PORT=str(port), HOST="127.0.0.1", NODE_ENV="production", LOG_LEVEL="error")
                process = subprocess.Popen(["node", "dist/index.js"], cwd=source, env=env, stdout=log, stderr=log)
                try:
                    base = f"http://127.0.0.1:{port}"
                    wait_ready(base, process)
                    subprocess.run(["node", str(ROOT / "tools/ci/seed-local-auth.cjs")], cwd=source, env=env,
                                   input=credentials.read_text(), text=True, stdout=log, stderr=log, check=True)
                    yield base, credentials
                finally:
                    process.terminate()
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
            else:
                raise ValueError("Native acceptance needs Linux or macOS")
