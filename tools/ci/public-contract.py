#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Validate/record the initial unauthenticated Seerr flow against the pinned container."""
import argparse
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
import json
from pathlib import Path
import sys
from urllib.request import build_opener, HTTPRedirectHandler
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/contract"))
from responses import validate_response
from validate import load_contract

OPERATIONS = [("public", "getStatus", "/status", "?checkUpdateAvailable=false"),
              ("settings", "getSettingsPublic", "/settings/public", "")]


def check_sunset(value, now):
    if value and parsedate_to_datetime(value) <= now + timedelta(days=90):
        raise ValueError("Called endpoint sunsets within 90 days")


def scrub(body):
    result = dict(body)
    if "plexClientIdentifier" in result:
        result["plexClientIdentifier"] = "00000000-0000-4000-8000-000000000000"
    if "vapidPublic" in result:
        result["vapidPublic"] = "REDACTED"
    return result


class RejectRedirects(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True)
    parser.add_argument("--record", action="store_true")
    args = parser.parse_args()
    address = urlsplit(args.base)
    if address.hostname not in {"127.0.0.1", "localhost"} or address.username or address.query or address.fragment:
        parser.error("Use a local test container without credentials")
    spec = load_contract(ROOT / "api")
    now = datetime.now(timezone.utc)
    records = []
    opener = build_opener(RejectRedirects())
    for tag, operation, path, query in OPERATIONS:
        with opener.open(args.base.rstrip("/") + "/api/v1" + path + query, timeout=20) as response:
            headers = {key.lower(): response.headers[key] for key in ("Content-Type", "Deprecation", "Sunset", "Link") if response.headers[key]}
            check_sunset(headers.get("sunset"), now)
            body = json.load(response)
            validate_response(spec, path, "GET", 200, body)
            if operation == "getStatus" and body.get("version") != "3.4.1":
                raise ValueError("Container is not the supported baseline")
            if operation == "getSettingsPublic" and body.get("initialized") is not False:
                raise ValueError("Initial recording expects the uninitialized scenario")
            records.append({"operationId": operation, "method": "GET", "path": "/api/v1" + path + query,
                            "status": response.status, "headers": headers, "body": f"{tag}/{operation}.json"})
            if args.record:
                output = ROOT / "api/fixtures/3.4.1" / tag / (operation + ".json")
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_text(json.dumps(scrub(body), indent=2) + "\n")
    if args.record:
        metadata = {"upstreamCommit": (ROOT / "api/UPSTREAM_COMMIT").read_text().splitlines()[0],
                    "recordedAt": now.isoformat(), "scenario": "uninitialized-public-probe",
                    "scrubbedFields": ["plexClientIdentifier", "vapidPublic"], "responses": records}
        (ROOT / "api/fixtures/3.4.1/recording.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print("public-contract: 2 live responses match the effective contract; no imminent sunset")


if __name__ == "__main__":
    main()
