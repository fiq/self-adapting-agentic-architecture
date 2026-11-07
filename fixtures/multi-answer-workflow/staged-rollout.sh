#!/usr/bin/env bash
# Held-out: rewards a rollout that is not all at once.
set -euo pipefail

workflow="$(dirname "$0")/workflow.txt"

if grep -q -x -E "rollout: (staged|canary)" "$workflow"; then
  echo "rollout is staged"
  exit 0
fi
echo "rollout is all at once"
exit 1
