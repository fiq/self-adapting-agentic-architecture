# CHG-017: Record engine routing defaults

## Why

`MODEL_ROUTING_POLICY.md` says how to use a session — reuse scope, reset
triggers, budget discipline, reviewer independence. It does not say *which
engine* to give which kind of work.

That decision was being made ad hoc every time. On 2026-08-22 the same choices
recurred often enough to be worth writing down: reviews to an external engine on
a separate budget, bounded implementation to a subagent in its own worktree,
anything touching the deterministic boundary kept with the lead.

## What

A `## Engine routing defaults` section in the existing policy, plus a guard so
its load-bearing controls cannot silently drift.

## The distinction that matters

These are **defaults a human or lead agent applies**, not automatic routing.
`Q-010` defers automatic provider selection pending measured usage and price
identity; `Q-011` defers preference and jurisdiction constraints. Writing down
what a person should reach for does not implement either, and the section says so
in its first line so it cannot be misread as prejudging them.

## Status

`proposed`, not canonical. These come from one day's observed use, not from
measurement. The section carries explicit revisit triggers, including the case
where a measured comparison contradicts a row.

## Not this change

- implementing automatic routing, or any runtime engine selection;
- changing how any engine is invoked;
- recording prices, which move and would date the document.

## Relates to

PAT-002, PAT-003, Q-010, Q-011.
