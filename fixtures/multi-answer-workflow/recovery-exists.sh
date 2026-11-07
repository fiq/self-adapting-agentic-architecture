#!/usr/bin/env bash
# Required: recovery stays on (manual or automatic). Allow-listed, so off, false or skip all fail.
set -euo pipefail
source "$(dirname "$0")/knob.sh"

if knob_is recovery "${RECOVERY_ON[@]}"; then
  echo "recovery is available"
  exit 0
fi
echo "recovery is not manual or automatic"
exit 1
