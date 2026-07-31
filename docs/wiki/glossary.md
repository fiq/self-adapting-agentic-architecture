# Glossary

Use this page for stable project terms that appear across specs, knowledge
entries, ADRs and handoffs. Prefer adding or clarifying a term here before
inventing a near-synonym in another Markdown file.

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

## Ontology

The project-owned vocabulary of concept types and relationship names used to
keep documentation traversable. The current ontology is the taxonomy in
`.agents/knowledge/TAXONOMY.md`; `Q-008` tracks whether it needs to become more
formal as Markdown volume grows.

## Wiki Page

A short human-facing explanation under `docs/wiki/`. Wiki pages summarize and
link to canonical specs, ADRs and knowledge nodes; they should not duplicate
TOON state or handoff history.
