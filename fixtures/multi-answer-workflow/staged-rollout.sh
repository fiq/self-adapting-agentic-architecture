#!/usr/bin/env bash
# Held-out: rewards a rollout that is not all at once.
set -euo pipefail
source "$(dirname "$0")/knob.sh"

if knob_is rollout "${STAGED_ROLLOUT[@]}"; then
  echo "rollout is staged"
  exit 0
fi
echo "rollout is all at once"
exit 1
