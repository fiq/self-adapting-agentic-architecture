#!/usr/bin/env bash
# Behaviour case: the publish policy must not allow publishing without a draft check.
set -euo pipefail

workflow="$(dirname "$0")/workflow.txt"

if grep -q '^draft-check: skip$' "$workflow"; then
  echo "publish guard is disabled: draft-check is skipped"
  exit 1
fi

echo "publish guard holds: draft-check is enforced"
