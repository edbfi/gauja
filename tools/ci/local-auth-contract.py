#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Record/validate local cookie authentication against an isolated, seeded Seerr."""
import argparse
from datetime import datetime, timezone
import http.cookiejar
import importlib.util
import json
from pathlib import Path
import sys
from urllib.parse import urlsplit
from urllib.request import build_opener, HTTPCookieProcessor, Request

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/contract"))
from responses import validate_response
from validate import load_contract

module_spec = importlib.util.spec_from_file_location("public_contract", ROOT / "tools/ci/public-contract.py")
public = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(public)

SECRET_FIELDS = {"password", "apiKey", "plexToken", "jellyfinAuthToken", "resetPasswordGuid", "secret", "sessionCookie"}


def scrub(value):
    if isinstance(value, list):
        return [scrub(item) for item in value]
    if isinstance(value, dict):
        result = {key: "REDACTED" if key in SECRET_FIELDS and item else scrub(item) for key, item in value.items()}
        return public.scrub(result)
    return value


def run(base, credentials, record=False, initialize=False):
    address = urlsplit(base)
    if address.scheme not in {"http", "https"} or address.hostname not in {"127.0.0.1", "localhost"} or address.username or address.query or address.fragment:
        raise ValueError("Use a local test server without URL credentials")
    spec = load_contract(ROOT / "api")
    now = datetime.now(timezone.utc)
    jar = http.cookiejar.CookieJar()
    opener = build_opener(public.RejectRedirects(), HTTPCookieProcessor(jar))
    records = []

    def call(path, method="GET", data=None, operation=None, tag=None, scenario=""):
        request = Request(base.rstrip("/") + "/api/v1" + path,
                          data=None if data is None else json.dumps(data).encode(),
                          method=method, headers={"Content-Type": "application/json"})
        with opener.open(request, timeout=20) as response:
            body = json.load(response)
            status = response.status
            headers = {key.lower(): response.headers[key] for key in ("Content-Type", "Deprecation", "Sunset", "Link") if response.headers[key]}
        public.check_sunset(headers.get("sunset"), now)
        if operation:
            validate_response(spec, path.split("?")[0], method, status, body)
            relative = f"{tag}/{operation}{scenario}.json"
            records.append({"operationId": operation, "method": method, "path": "/api/v1" + path,
                            "status": status, "headers": headers, "body": relative})
            if record:
                output = ROOT / "api/fixtures/3.4.1" / relative
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_text(json.dumps(scrub(body), indent=2) + "\n")
        return body

    call("/auth/local", "POST", credentials, "postAuthLocal", "auth")
    if not any(cookie.name == "connect.sid" for cookie in jar):
        raise ValueError("Local sign-in did not establish a session cookie")
    if initialize:
        # Excluded from app coverage; this only prepares the test server.
        call("/settings/initialize", "POST", {})
    user = call("/auth/me", operation="getAuthMe", tag="auth")
    if user.get("email") != credentials["email"]:
        raise ValueError("Cookie authenticated the wrong test user")
    call("/status?checkUpdateAvailable=false", operation="getStatus", tag="public", scenario="-initialized")
    settings = call("/settings/public", operation="getSettingsPublic", tag="settings", scenario="-initialized")
    if settings.get("initialized") is not True:
        raise ValueError("Local auth scenario requires an initialized server")
    call("/auth/logout", "POST", {}, "postAuthLogout", "auth")
    if record:
        metadata = {"upstreamCommit": (ROOT / "api/UPSTREAM_COMMIT").read_text().splitlines()[0],
                    "recordedAt": now.isoformat(), "scenario": "initialized-local-auth",
                    "scrubbedFields": sorted(SECRET_FIELDS | {"plexClientIdentifier", "vapidPublic"}),
                    "responses": records}
        (ROOT / "api/fixtures/3.4.1/recording-auth.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print("local-auth-contract: live session, user, public settings and logout validated")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True)
    parser.add_argument("--credentials", type=Path, required=True, help="Ignored test credentials file; never recorded")
    parser.add_argument("--record", action="store_true")
    parser.add_argument("--initialize", action="store_true", help="Test harness only")
    args = parser.parse_args()
    try:
        run(args.base, json.loads(args.credentials.read_text()), args.record, args.initialize)
    except Exception:
        # HTTP/validation exceptions can contain credential-bearing request descriptions.
        parser.exit(1, "local-auth-contract: failed; inspect the operation using a redacted test harness\n")
