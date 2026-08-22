# CHG-015: Make independent review and consolidation part of the contract

## Why

On 2026-08-22 every independent review pass found something the previous
reviewer and the lead had both missed — twice in the lead's own work. One was an
acceptance test that could not fail; the other was an audit write that could
report a passing gate for a candidate whose checks had failed. Both are defects
the lead had spent that same session finding in other people's work. Both passed
CI.

None of that was recorded anywhere durable. It lived in a session and in
`HANDOFF.toon`, which is session state and ages out.

## What

Two things, at two levels.

The **framework** gains a required section, stated generally, so any project
generated from `.agentic-template` inherits the rule rather than rediscovering
it. The **project** gains the corresponding section in `AGENTS.md`, plus a
contract guard so the section is load-bearing rather than prose that drifts.

The rule itself distinguishes two things that both get called "running an
agent". Reviews are read-only, so several can run on one change and their
findings are additive. Implementation agents write, so two on one file buys
merge conflicts rather than speed.

## Not this change

- automating reviewer dispatch or consolidation;
- changing how any specific reviewer is invoked;
- anything touching the deterministic decision boundary.

## Relates to

PAT-003, PAT-002, PAT-001, LRN-001, ARCH-001.
