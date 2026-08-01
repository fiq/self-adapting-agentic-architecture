---
id: Q-009
type: question
status: open
title: Quarkus runtime shell decision
summary: Decide whether Quarkus should host SAAA when it becomes a long-running bridge or service runtime.
context: SAAA is currently a local CLI and adapter spine. Quarkus may become useful for a long-running OpenAI-compatible facade, MCP server lifecycle, health checks, metrics, config and packaging, but adopting it now would add framework gravity before the runtime shape is proven.
relates_to:
  - ADR-0003
  - SYS-001
owner: architect
blocking: false
discovered_during: PR #13 cleanup discussion
next_action: Defer Quarkus until SAAA needs a long-running northbound bridge runtime; use focused libraries such as WireMock and SmallRye Config behind adapter-owned boundaries now.
resolution: null
answered_by: []
evidence:
  - docs/decisions/0003-smart-bridge-northbound-interfaces.md
  - PROJECT_PROFILE.toon
---

# Quarkus Runtime Shell Decision

## Context

Quarkus is a credible Java runtime if SAAA becomes a long-running smart bridge:
HTTP/OpenAI-compatible northbound facade, MCP server lifecycle, health checks,
metrics, config and packaging. The current slice is still a local CLI and
adapter boundary, so adopting Quarkus now would solve a later runtime problem
while increasing framework surface in the present code.

## Current Evidence

- `ADR-0003` records the smart-bridge direction but does not implement the
  northbound service runtime.
- `CHG-004` currently needs provider configuration and protocol tests, which
  are handled by focused libraries behind adapter boundaries.
- The deterministic/domain layers must remain framework-free.

## Next Action

Keep Quarkus deferred. Revisit when SAAA needs a long-running HTTP/MCP runtime,
health/metrics, service packaging or native-image deployment.
