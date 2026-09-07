#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Secret-logging guard (PRD §10, §14.1 `check-secret-logging`): no log call may format a
# value from the secrets layer. Names cover the concrete encrypted stores, Keychain,
# opaque Secret wrapper, and credential-bearing transport fields.
#
# Sources are walked character by character with string, comment and parenthesis state, so a log
# call is scanned from its first token to the parenthesis that closes it (following chained
# `.method(` continuations), wherever line breaks fall. Parentheses and quotes inside string
# literals (`"..."`, `"""..."""`, Swift `#"..."#`), char literals and comments do not count, and a
# log call inside a comment is not a log call.
# Heuristic guard, not a Kotlin/Swift parser; supplement with review and redaction tests.
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: tools/ci/check-secret-logging.sh [--help] [--symbols A,B,C] [PATH...]

Scans Kotlin and Swift sources under PATH... (default: apps) for log calls
(Log.*, Timber.*, Logger.*, logger.*, print(, println(, debugPrint(, NSLog(, os_log()
that mention a secret-layer symbol, including calls split across lines.
Generated directories and local build/dependency caches are skipped.
  --symbols  comma-separated identifiers
             (default: SecretStore,EncryptedSecretStore,KeychainStore,Secret,apiKey,sessionCookie,plexToken,basicAuthPassword,basicPassword,secrets)
Exits 0 when there are no sources yet; 1 on any offending line.
USAGE
}

symbols="SecretStore,EncryptedSecretStore,KeychainStore,Secret,apiKey,sessionCookie,plexToken,basicAuthPassword,basicPassword,secrets"
paths=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --help|-h) usage; exit 0 ;;
    --symbols) symbols="$2"; shift 2 ;;
    --symbols=*) symbols="${1#--symbols=}"; shift ;;
    *) paths+=("$1"); shift ;;
  esac
done
[[ ${#paths[@]} -eq 0 ]] && paths=("apps")

existing=()
for p in "${paths[@]}"; do [[ -e "$p" ]] && existing+=("$p"); done
if [[ ${#existing[@]} -eq 0 ]]; then
  echo "check-secret-logging: no sources under ${paths[*]} yet; nothing to scan."
  exit 0
fi

# GENERATED paths from prek.toml; generated code never logs and is never hand-edited.
generated_regex='(apps/android/core/api/|apps/ios/Packages/SeerrAPI/Generated/|apps/android/core/designsystem/src/main/kotlin/.*/generated/|apps/ios/Packages/DesignSystem/Sources/DesignSystem/Generated/)'
# `[Ll]ogger(\.[a-z]+)?\(` starts at a `logger.debug(` call or at a `Logger(...)` initializer whose
# chained `.debug(` is followed by the scanner, whatever the initializer's arguments look like.
log_call='(Log\.[a-z]+\(|Timber(\.tag\([^)]*\))?\.[a-z]+\(|[Ll]ogger(\.[a-z]+)?\(|print\(|println\(|debugPrint\(|NSLog\(|os_log\()'
symbol_alt="$(printf '%s' "$symbols" | tr ',' '|')"
symbol_regex="(^|[^A-Za-z0-9_])(${symbol_alt})([^A-Za-z0-9_]|$)"

status=0
# Regexes reach awk through the environment: `-v` would reinterpret their backslashes.
scan_file() {
  LC_ALL=C LOG_CALL="$log_call" SYMBOL_REGEX="$symbol_regex" awk '
    BEGIN {
      log_call = ENVIRON["LOG_CALL"]; symbol_regex = ENVIRON["SYMBOL_REGEX"]
      chain = "^[.][A-Za-z_][A-Za-z0-9_]*[[:space:]]*[(]"
    }
    { text = text $0 "\n" }
    END { walk() }

    function report(call_end,   call) {
      call = substr(text, call_start, call_end - call_start + 1)
      if (call ~ symbol_regex && call_line != last_line) { print call_line; last_line = call_line }
    }

    # mode: code | str (closing delimiter in `closing`) | char | line (comment) | block (comment).
    # in_call/depth track the current log call; pending means its parentheses just balanced and a
    # chained `.method(` may still follow after whitespace or comments.
    function walk(   n, i, c, two, line, mode, closing, escapes, depth, in_call, pending, next_cand) {
      n = length(text); i = 1; line = 1; mode = "code"; next_cand = 0
      while (i <= n) {
        c = substr(text, i, 1)
        if (mode == "line") { if (c == "\n") { mode = "code"; line++ }; i++; continue }
        if (mode == "block") { if (substr(text, i, 2) == "*/") { mode = "code"; i += 2 } else { if (c == "\n") line++; i++ }; continue }
        if (mode == "str") {
          if (substr(text, i, length(closing)) == closing) { mode = "code"; i += length(closing) }
          else if (escapes && c == "\\") i += 2
          else { if (c == "\n") line++; i++ }
          continue
        }
        if (mode == "char") { if (c == "\\") i += 2; else { if (c == "\x27") mode = "code"; i++ }; continue }
        if (c == "\n") { line++; i++; continue }
        two = substr(text, i, 2)
        if (pending) {
          if (c ~ /[[:space:]]/) { i++; continue }
          if (two != "//" && two != "/*") {
            if (substr(text, i) ~ chain) pending = 0
            else { report(call_end); in_call = 0; pending = 0 }
          }
        }
        if (two == "//") { mode = "line"; i += 2; continue }
        if (two == "/*") { mode = "block"; i += 2; continue }
        if (substr(text, i, 3) == "\"\"\"") { mode = "str"; closing = "\"\"\""; escapes = 0; i += 3; continue }
        if (c == "\"") { mode = "str"; closing = "\""; escapes = 1; i++; continue }
        if (c == "#" && match(substr(text, i, 64), /^#+"/)) {
          # Swift extended string: #"..."# or #"""..."""#; the closing quote needs the same hashes.
          closing = "\"" substr(text, i, RLENGTH - 1); escapes = 0; mode = "str"; i += RLENGTH
          if (substr(text, i - 1, 3) == "\"\"\"") { closing = "\"\"" closing; i += 2 }
          continue
        }
        if (c == "\x27") { mode = "char"; i++; continue }
        if (in_call) {
          if (c == "(") depth++
          else if (c == ")") { depth--; if (depth <= 0) { pending = 1; call_end = i } }
          i++; continue
        }
        if (next_cand < i) next_cand = match(substr(text, i), log_call) ? i + RSTART - 1 : n + 1
        if (i == next_cand) { in_call = 1; depth = 0; call_start = i; call_line = line }
        else i++
      }
      if (in_call) report(pending ? call_end : n)
    }
  ' "$1"
}

while IFS= read -r file; do
  hits="$(scan_file "$file")"
  if [[ -n "$hits" ]]; then
    status=1
    while IFS= read -r line; do
      echo "check-secret-logging: $file:$line: log call formats a secret-layer symbol" >&2
    done <<<"$hits"
  fi
done < <(find "${existing[@]}" \( -type d \( -name .build -o -name .gradle -o -name build -o -name DerivedData \) -prune \) -o \
  \( -type f \( -name '*.kt' -o -name '*.swift' \) -print \) | grep -Ev "$generated_regex" || true)

if [[ $status -ne 0 ]]; then
  echo "check-secret-logging: secrets never touch logs; redact through core/common / Common (PRD §10)." >&2
  exit 1
fi
echo "check-secret-logging: no secret-layer symbols inside log calls."
