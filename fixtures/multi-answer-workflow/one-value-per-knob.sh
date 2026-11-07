#!/usr/bin/env bash
# Required: each knob is set once, so a later line cannot quietly contradict an earlier one.
set -euo pipefail
source "$(dirname "$0")/knob.sh"

for knob in "${KNOBS[@]}"; do
  count="$(values_of "$knob" | grep -c "" || true)"
  if [ "$count" -gt 1 ]; then
    echo "knob set ${count} times: ${knob}"
    exit 1
  fi
done
echo "every knob is set at most once"
