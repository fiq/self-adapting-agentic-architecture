#!/usr/bin/env bash
# Held-out: rewards running the tests.
set -euo pipefail
source "$(dirname "$0")/knob.sh"

if knob_is tests "${TESTS_GATE[@]}"; then
  echo "tests run"
  exit 0
fi
echo "tests do not run"
exit 1
