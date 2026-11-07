#!/usr/bin/env bash
# Required: recovery must not be removed or disabled.
set -euo pipefail

workflow="$(dirname "$0")/workflow.txt"

if grep -q -x -E "recovery: (none|off|disabled)" "$workflow"; then
  echo "recovery is disabled"
  exit 1
fi
echo "recovery is available"
