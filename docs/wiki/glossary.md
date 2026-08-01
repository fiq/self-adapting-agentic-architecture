# Glossary

Use this page for stable project terms that appear across specs, knowledge
entries, ADRs and handoffs. Prefer adding or clarifying a term here before
inventing a near-synonym in another Markdown file.

## Agentic Control Plane

The product role where SAAA coordinates model calls, context, validation,
scoring and audit rather than acting as another model. `ADR-0003` uses this
term with "smart bridge".

## Agentic Loop

The workflow that proposes, realizes, checks, scores and records candidate
changes. Models may propose or repair, but deterministic code validates and
scores.

## Capability

A living behavior contract under `specs/capabilities/`, keyed as `CAP-*`.
Capability pages say what the system promises after accepted changes land.

## Change

An in-flight or historical proposal under `specs/changes/<id>/`, keyed as
`CHG-*`. Changes map scenarios to tests and tasks.

## Knowledge Node

A structured Markdown entry under `.agents/knowledge/`, keyed by its category
prefix such as `ARCH-*`, `CON-*`, `Q-*` or `RISK-*`. Knowledge nodes preserve
durable facts, open questions and decisions without turning the wiki into a
changelog.

## Northbound Interface

An interface exposed by SAAA to callers. `ADR-0003` names MCP and a narrow
OpenAI-compatible API as complementary northbound interfaces.

## Ontology

The project-owned vocabulary of concept types and relationship names used to
keep documentation traversable. The current ontology is the taxonomy in
`.agents/knowledge/TAXONOMY.md`; `Q-008` tracks whether it needs to become more
formal as Markdown volume grows.

## Smart Bridge

The product role where clients call SAAA first, and SAAA routes to existing
models or agents while adding context packaging, validation, scoring and audit.
This is distinct from SAAA pretending to be a better model.

## Southbound Agent Adapter

An adapter SAAA uses to call an external agent rather than a raw model endpoint.
The same deterministic boundary applies: an external agent may propose or
repair, but SAAA still validates and scores.

## Southbound Adapter

An adapter SAAA uses to call an underlying model, datastore or tool.
`CHG-004`'s OpenAI-compatible LangChain4j wiring is southbound.

## Wiki Page

A short human-facing explanation under `docs/wiki/`. Wiki pages summarize and
link to canonical specs, ADRs and knowledge nodes; they should not duplicate
TOON state or handoff history.
