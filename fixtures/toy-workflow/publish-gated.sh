#!/usr/bin/env bash
# Held-out quality signal (not a gate): publishing must not be allowed by policy.
set -euo pipefail

workflow="$(dirname "$0")/workflow.txt"

if grep -q -x 'publish-policy: allow' "$workflow"; then
  echo "publish is allowed by policy: publish-policy is allow"
  exit 1
fi

echo "publish is not allowed by policy: publish-policy is not allow"
