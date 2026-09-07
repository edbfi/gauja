#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Check resolved app dependencies against deny.toml; missing license metadata fails closed."""
import argparse
from functools import lru_cache
import json
import hashlib
from pathlib import Path
import tomllib
from urllib.error import HTTPError
from urllib.request import urlopen
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
NS = {"p": "http://maven.apache.org/POM/4.0.0"}
NAMES = {
    "Apache 2": "Apache-2.0", "Apache 2.0": "Apache-2.0", "ASL, version 2": "Apache-2.0",
    "Apache License 2": "Apache-2.0",
    "Apache License V2.0": "Apache-2.0", "Apache License v2.0": "Apache-2.0",
    "New BSD License": "BSD-3-Clause",
    "Bouncy Castle Licence": "MIT",
    "Eclipse Distribution License - v 1.0": "BSD-3-Clause", "EDL 1.0": "BSD-3-Clause",
    "Mozilla Public License 1.1 (MPL 1.1)": "MPL-1.1",
    "GNU LESSER GENERAL PUBLIC LICENSE 2.1": "LGPL-2.1-only",
    "CDDL + GPLv2 with classpath exception": "CDDL-1.1",
    "Apache License, Version 2.0": "Apache-2.0", "The Apache License, Version 2.0": "Apache-2.0",
    "The Apache Software License, Version 2.0": "Apache-2.0", "The MIT License": "MIT",
    "MIT License": "MIT", "BSD 3-Clause": "BSD-3-Clause",
    "Eclipse Public License v1.0": "EPL-1.0", "Eclipse Public License 1.0": "EPL-1.0",
    "Eclipse Public License v2.0": "EPL-2.0", "Eclipse Public License 2.0": "EPL-2.0",
    "LGPL, version 2.1": "LGPL-2.1-only", "Unicode-3.0": "Unicode-3.0",
}


def accepted(licenses, scope, policy, dependency=""):
    if any(value in policy.get("deny", []) for value in licenses):
        return False
    if scope == "build":
        permitted_exception = policy.get("build-exceptions", {}).get(dependency)
        if isinstance(permitted_exception, str):
            permitted_exception = [permitted_exception]
        if permitted_exception and set(licenses) == set(permitted_exception):
            return True
    permitted = set(policy["allow"])
    if scope == "build":
        permitted.update(policy["allow-build-only"])
    return bool(licenses) and all(value in permitted for value in licenses)


@lru_cache(maxsize=None)
def pom(group, name, version):
    cache = ROOT / ".cache/licenses/poms" / group / name / version
    cache.mkdir(parents=True, exist_ok=True)
    destination = cache / (name + ".pom")
    if not destination.exists():
        local = Path.home() / ".gradle/caches/modules-2/files-2.1" / group / name / version
        found = next(local.glob("*/*.pom"), None)
        if found:
            destination.write_bytes(found.read_bytes())
        else:
            suffix = group.replace(".", "/") + f"/{name}/{version}/{name}-{version}.pom"
            for base in ["https://dl.google.com/dl/android/maven2/", "https://repo.maven.apache.org/maven2/", "https://plugins.gradle.org/m2/"]:
                try:
                    with urlopen(base + suffix, timeout=30) as response:
                        destination.write_bytes(response.read())
                    break
                except HTTPError as error:
                    if error.code not in (403, 404):
                        raise
            else:
                raise ValueError(f"No POM: {group}:{name}:{version}")
    document = ET.fromstring(destination.read_bytes())
    if not document.tag.startswith("{"):
        for element in document.iter():
            element.tag = "{" + NS["p"] + "}" + element.tag
    return document


@lru_cache(maxsize=None)
def maven_licenses(group, name, version):
    document = pom(group, name, version)
    coordinate = f"{group}:{name}:{version}"
    reviews = json.loads((ROOT / "tools/ci/license-metadata.json").read_text())
    if coordinate in reviews:
        review = reviews[coordinate]
        data = (ROOT / ".cache/licenses/poms" / group / name / version / (name + ".pom")).read_bytes()
        if hashlib.sha256(data).hexdigest() != review["pom_sha256"]:
            raise ValueError(f"License review no longer matches POM: {coordinate}")
        return review["licenses"]
    values = document.findall("p:licenses/p:license/p:name", NS)
    if values:
        return [NAMES.get(value.text, value.text) for value in values]
    parent = document.find("p:parent", NS)
    if parent is not None:
        parts = [parent.findtext("p:" + key, namespaces=NS) for key in ("groupId", "artifactId", "version")]
        if all(parts):
            return maven_licenses(*parts)
    return []


def shipping_modules(graph):
    modules = {module["name"]: module for module in graph}
    if ":app" not in modules:
        raise ValueError("The resolved graph has no app module")
    shipped = set()
    pending = [":app"]
    while pending:
        name = pending.pop()
        if name in shipped:
            continue
        shipped.add(name)
        for edge in modules[name]["edges"]:
            scope = edge["scope"].lower()
            if "test" not in scope and "compileonly" not in scope:
                pending.append(edge["target"])
    return shipped


def android(root):
    tree = root / "apps/android"
    graph = json.loads((tree / "build/reports/module-graph.json").read_text())
    if not graph:
        raise ValueError("The evaluated module graph is empty")
    modules = [tree / module["name"].strip(":").replace(":", "/") for module in graph]
    reports = [path / "build/reports/resolved-dependencies.json" for path in [tree, tree / "build-logic", *modules]]
    if any(not path.exists() for path in reports):
        raise ValueError("Run Gradle exportResolvedDependencies for every module first")
    shipped = {tree / name.strip(":").replace(":", "/") for name in shipping_modules(graph)}
    dependencies = {}
    for report in reports:
        for value in json.loads(report.read_text()):
            key = tuple(value[name] for name in ("group", "name", "version"))
            scope = value["scope"] if report.parent.parent.parent in shipped else "build"
            dependencies[key] = "runtime" if "runtime" in (dependencies.get(key), scope) else "build"
    if not dependencies:
        raise ValueError("The resolved Maven graph is empty")
    for coordinate, scope in sorted(dependencies.items()):
        yield {"dependency": ":".join(coordinate), "scope": scope, "licenses": maven_licenses(*coordinate)}


def swift_license(text):
    if "Permission is hereby granted, free of charge" in text and "THE SOFTWARE IS PROVIDED" in text:
        return ["MIT"]
    if "Apache License" in text and "Version 2.0, January 2004" in text:
        return ["Apache-2.0"]
    return []


def swift_packages(packages, tooling=False):
    state = json.loads((packages / "workspace-state.json").read_text())
    dependencies = state["object"]["dependencies"]
    if not any(item["state"]["name"] == "sourceControlCheckout" for item in dependencies):
        raise ValueError("The resolved Swift package graph is empty")
    for dependency in dependencies:
        if dependency["state"]["name"] != "sourceControlCheckout":
            continue
        reference = dependency["packageRef"]
        checkout = packages / "checkouts" / dependency["subpath"]
        licenses = list(checkout.glob("LICENSE*"))
        values = sorted({value for path in licenses for value in swift_license(path.read_text())})
        identity = reference["identity"]
        revision = dependency["state"]["checkoutState"]["revision"]
        yield {"dependency": identity + "@" + revision, "scope": "build" if tooling or identity in {"swiftlintplugins", "swift-syntax"} else "runtime", "licenses": values}


def ios(root):
    app = swift_packages(root / "apps/ios/DerivedData/SourcePackages")
    generator = swift_packages(root / "tools/codegen/ios/.build", tooling=True)
    dependencies = {}
    for item in [*app, *generator]:
        previous = dependencies.get(item["dependency"])
        if previous is None or item["scope"] == "runtime":
            dependencies[item["dependency"]] = item
    yield from sorted(dependencies.values(), key=lambda item: item["dependency"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["android", "ios"])
    args = parser.parse_args()
    policy = tomllib.loads((ROOT / "deny.toml").read_text())["licenses"]
    report = list((android if args.platform == "android" else ios)(ROOT))
    output = ROOT / ".cache/licenses" / (args.platform + ".json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n")
    failures = [item for item in report if not accepted(item["licenses"], item["scope"], policy, item["dependency"])]
    for item in failures:
        print(f"{item['dependency']} ({item['scope']}): {item['licenses'] or 'UNKNOWN'}")
    print(f"licenses: {len(report)} resolved dependencies, {len(failures)} rejected; {output}")
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
