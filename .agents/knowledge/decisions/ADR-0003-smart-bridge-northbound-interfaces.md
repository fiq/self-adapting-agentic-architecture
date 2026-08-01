---
id: ADR-0003
type: decision
title: Smart bridge northbound interfaces
status: proposed
summary: SAAA should expose both MCP for tool-aware agents and a narrow OpenAI-compatible API for existing clients that want SAAA as a governed bridge in front of multiple models and external agents.
owners:
  - architect
tags:
  - product-direction
  - interface
relates_to:
  - ADR-0002
  - ARCH-001
  - SYS-001
supersedes: []
superseded_by: null
evidence:
  - docs/decisions/0003-smart-bridge-northbound-interfaces.md
  - specs/changes/CHG-004-live-mcp-and-l3-utility/
reviewed_at: 2026-08-01
review_after: 2026-10-31
---

# ADR-0003: Smart Bridge Northbound Interfaces

Details live in
`docs/decisions/0003-smart-bridge-northbound-interfaces.md`. This knowledge
node exists so specs, wiki pages and future interface changes can link the
decision by id.

## Summary

SAAA is a smart bridge and agentic control plane in front of existing models and
agents, including OpenAI, Claude, local models and future external agent APIs.
It should expose two northbound integration surfaces:

- MCP for tool-aware agents that can call explicit operations.
- A narrow OpenAI-compatible API for existing SDKs, gateways and eval harnesses
  that want to point at SAAA as the base URL.

Southbound OpenAI-compatible model wiring is not the same thing as the future
northbound OpenAI-compatible facade. `CHG-004` starts with southbound provider
wiring; the northbound facade is a later slice.

Routing across multiple external agents remains a later slice, but this ADR
requires the architecture to keep provider and agent details behind adapters so
deterministic scoring and approval policy stay unchanged.
