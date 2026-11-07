#!/usr/bin/env bash
# Held-out: rewards running the tests.
set -euo pipefail

workflow="$(dirname "$0")/workflow.txt"

if grep -q -x "tests: run" "$workflow"; then
  echo "tests run"
  exit 0
fi
echo "tests do not run"
exit 1
