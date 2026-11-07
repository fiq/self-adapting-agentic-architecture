#!/usr/bin/env bash
# Required: a release must be gated by at least one of review or tests.
set -euo pipefail

workflow="$(dirname "$0")/workflow.txt"

# Passes on any one of two independent gates, so several different edits satisfy it.
if grep -q -x -E "review: required|tests: run" "$workflow"; then
  echo "release is gated: review is required or tests run"
  exit 0
fi
echo "release is not gated: neither review: required nor tests: run is set"
exit 1
