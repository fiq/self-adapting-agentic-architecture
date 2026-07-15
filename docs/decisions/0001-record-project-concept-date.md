# ADR: Record Project Concept Date

## Status

Accepted

## Context

The decision to create this reusable AI-first software project template dates to
Saturday, 16 May 2026.

## Decision

Record `2026-05-16` as the project concept date in repository metadata.

Git commit timestamps remain implementation timestamps. This keeps project
origin metadata visible without making commit history carry that meaning.

## Consequences

The repository preserves both:

- the project concept date
- the actual implementation history

Future generated projects may keep, revise or remove this value during `/specialise`
when the project profile is specialised.

## Evidence

- `PROJECT_PROFILE.toon`
- `CUSTOMIZE_THIS_PROJECT.toon`
- `HANDOFF.toon`
