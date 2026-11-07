#!/usr/bin/env bash
# Required: every knob is still there with a value, so deleting lines is not an answer.
set -euo pipefail
source "$(dirname "$0")/knob.sh"

for knob in "${KNOBS[@]}"; do
  if [ -z "$(values_of "$knob" | grep -v "^$" || true)" ]; then
    echo "knob missing or empty: ${knob}"
    exit 1
  fi
done
echo "all five knobs present"
