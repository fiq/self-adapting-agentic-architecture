#!/usr/bin/env bash
# Required: an answer must keep all five knobs, so deleting lines is not an answer.
set -euo pipefail

workflow="$(dirname "$0")/workflow.txt"

for knob in review tests rollout notify recovery; do
  if ! grep -q -E "^${knob}: .+$" "$workflow"; then
    echo "knob missing: ${knob}"
    exit 1
  fi
done
echo "all five knobs present"
