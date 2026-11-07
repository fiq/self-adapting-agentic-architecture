#!/usr/bin/env bash
# Required: a release is gated by review or by tests.
set -euo pipefail
source "$(dirname "$0")/knob.sh"

if knob_is review "${REVIEW_GATE[@]}" || knob_is tests "${TESTS_GATE[@]}"; then
  echo "release is gated: review is required or tests run"
  exit 0
fi
echo "release is not gated: set review to required or tests to run"
exit 1
