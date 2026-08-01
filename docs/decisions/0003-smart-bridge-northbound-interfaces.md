# ADR-0003: Smart Bridge Northbound Interfaces

## Status

Proposed

## Context

SAAA is intended to sit in front of existing agents and models, not to become
another model competing with them. Those underlying systems may include OpenAI,
Claude, local OpenAI-compatible servers, specialist agents and future hosted
agent APIs. The near-term implementation is a local Java CLI with LangChain4j
adapters because that is the smallest auditable spine for candidate evaluation,
but the product shape needs a clear interface direction.

Two integration styles matter:

- tool-aware agents and IDEs can call explicit tools;
- existing applications, gateways and eval harnesses often already speak an
  OpenAI-compatible API.

If SAAA only exposes MCP, existing OpenAI SDK users cannot adopt it as a smart
bridge without custom client work. If SAAA only exposes an OpenAI-compatible
API, tool-aware agents lose the explicit, typed operations that make audit and
control easier.

## Decision

SAAA will treat MCP and an OpenAI-compatible API as complementary northbound
interfaces:

- **MCP** is the tool-native interface for outer agents. It exposes explicit
  operations such as `evolve`, `score`, `inspect-candidate`, `read-journal` and
  later promotion-oriented commands. It is the clearest surface for agents that
  already understand tools.
- **OpenAI-compatible API** is the compatibility bridge for clients that expect
  `/v1/chat/completions`-style interaction. It lets users point existing SDKs,
  gateways and eval harnesses at SAAA while SAAA performs routing, context
  packaging, validation, scoring and audit work before calling underlying
  models or external agents.

Southbound adapters must remain provider-neutral at the deterministic boundary.
OpenAI-compatible endpoints are one useful protocol, not the product's only
model family. Claude, OpenAI, local models and external agent APIs should be
reachable through adapters and routing policy without exposing their provider
types to `modules/domain` or `modules/deterministic`.

The OpenAI-compatible northbound surface is deliberately not a full OpenAI API
clone at first. Start narrow with chat-completions-compatible request handling,
clear SAAA model aliases such as `saaa/default`, `saaa/evolve` and
`saaa/review`, and explicit metadata for candidate ids, checks, scores and
audit references where the response shape supports it.

Southbound model adapters remain separate. `CHG-004`'s OpenAI-compatible
LangChain4j wiring is southbound: SAAA calling a model endpoint. This ADR adds
the product direction for a later northbound OpenAI-compatible facade:
clients calling SAAA as a smart bridge.

## Consequences

**Benefits.**

- Existing agent tools can use MCP without losing typed control.
- Existing OpenAI SDK users can adopt SAAA by changing a base URL and model
  name, then opt into governed behavior over time.
- Provider and agent routing can evolve without changing deterministic scoring
  or approval policy.
- The product story becomes "governed model bridge / agentic control plane"
  rather than "another model" or "only a Java CLI".

**Costs and risks.**

- The two northbound interfaces must not drift into inconsistent authority
  rules. Neither surface may override deterministic validation, scoring,
  promotion or rollback.
- OpenAI-compatible clients may expect broad endpoint coverage. The first
  slice must document the deliberately narrow compatibility scope.
- Streaming, tool-call compatibility and provider-specific extensions should
  be deferred until a concrete client needs them.
- Routing among multiple external agents needs explicit audit metadata so a run
  records which model or agent proposed, repaired or reviewed each candidate.

## Review Conditions

- Revisit before implementing the northbound OpenAI-compatible facade.
- Revisit if MCP clients need richer typed results than chat-completions-shaped
  responses can represent.
- Revisit if OpenAI-compatible clients require endpoint coverage beyond
  chat-completions-compatible requests.
