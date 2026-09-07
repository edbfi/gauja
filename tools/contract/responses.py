# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Validate OpenAPI 3.0 response bodies without printing their contents."""
from jsonschema import Draft4Validator, validators


def nullable_type(validator, expected, instance, schema):
    if instance is None and schema.get("nullable") is True:
        return
    yield from Draft4Validator.VALIDATORS["type"](validator, expected, instance, schema)


OpenAPIResponseValidator = validators.extend(Draft4Validator, {"type": nullable_type})


def validate_response(spec, path, method, status, body):
    pointer = "#/paths/" + path.replace("~", "~0").replace("/", "~1")
    pointer += f"/{method.lower()}/responses/{status}/content/application~1json/schema"
    validator = OpenAPIResponseValidator(dict(spec, **{"$ref": pointer}))
    if next(validator.iter_errors(body), None) is not None:
        raise ValueError(f"Response schema mismatch: {method} {path} {status}")
