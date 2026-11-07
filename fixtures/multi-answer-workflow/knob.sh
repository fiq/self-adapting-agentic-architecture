#!/usr/bin/env bash
# Shared by the case scripts; not a case itself. Reads workflow.txt the way a person would:
# case-insensitive knob names and values, spaces around ':' and at line ends ignored, '#' lines skipped.

workflow="$(dirname "${BASH_SOURCE[0]}")/workflow.txt"

# Prints every value set for a knob, lowercased and trimmed, one per line.
values_of() {
  awk -v knob="$1" '
    /^[[:space:]]*#/ { next }
    {
      line = tolower($0)
      if (match(line, "^[[:space:]]*" knob "[[:space:]]*:")) {
        value = substr(line, RLENGTH + 1)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
        print value
      }
    }' "$workflow"
}

# Succeeds when the knob is set exactly once and its value is one of the given words.
knob_is() {
  local knob="$1"; shift
  local values; values="$(values_of "$knob")"
  [ "$(printf '%s' "$values" | grep -c '')" -eq 1 ] || return 1
  local allowed
  for allowed in "$@"; do
    [ "$values" = "$allowed" ] && return 0
  done
  return 1
}

KNOBS=(review tests rollout notify recovery)
REVIEW_GATE=(required require mandatory enforce enforced)
TESTS_GATE=(run required always enforce enforced)
STAGED_ROLLOUT=(staged canary phased gradual)
RECOVERY_ON=(manual automatic auto)
