#!/usr/bin/env bash
# Held-out: rewards requiring a review.
set -euo pipefail
source "$(dirname "$0")/knob.sh"

if knob_is review "${REVIEW_GATE[@]}"; then
  echo "review is required"
  exit 0
fi
echo "review is not required"
exit 1
