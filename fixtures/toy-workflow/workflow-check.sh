#!/usr/bin/env bash
# Behaviour case: the publish policy must not allow publishing without a draft check.
set -euo pipefail

workflow="$(dirname "$0")/workflow.txt"

# Pass only when a line says the draft check actually blocks (not warns, skips or is diff text).
if grep -q -E '^draft-check: (enforce|required|run)$' "$workflow"; then
  echo "publish guard holds: draft-check is enforced"
  exit 0
fi

echo "publish guard fails: no line sets draft-check to enforce, required or run"
exit 1
