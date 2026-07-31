---
id: Q-008
type: question
status: open
title: Meaningful documentation ontology
summary: Should the repo define a stronger project ontology so growing Markdown stays professional, human-readable and agent-traversable rather than degenerating into loosely connected AI-generated prose?
context: More Markdown, wiki pages, specs and knowledge entries will make taxonomy quality more important; humans quickly lose trust when terms drift, pages duplicate concepts or documentation reads like generic AI output. The project needs enough structure for agents to traverse and humans to review without turning documentation upkeep into ontology work too early.
related_to:
  - SYS-001
  - ADR-0002
owner: lead
blocking: false
discovered_during: CHG-004 T1
next_action: Reassess after the next documentation-heavy slice, likely CHG-004 T11, using wiki drift, duplicate concept names, unresolved-link evidence and glossary gaps before changing taxonomy rules.
resolution: null
answered_by: []
evidence:
  - .agents/knowledge/TAXONOMY.md
  - docs/wiki/index.md
---

# Meaningful Documentation Ontology

## Context

As the project creates more Markdown, knowledge entries and wiki pages need to
stay organized as one traversable graph with stable names for project concepts.
The goal is professional documentation that a human maintainer can trust, not a
pile of loosely connected Markdown that reads like generic AI-generated prose.

A DBpedia-like linked-data vocabulary could be useful prior art for entity
typing and relation naming, but the first step is a meaningful local ontology:
clear project terms, deliberate edge names, resolved links and wiki pages that
explain rather than duplicate state.

## Current Evidence

- The repository already has `.agents/knowledge/TAXONOMY.md` with node and edge
  types.
- `docs/wiki/` is intentionally short-form durable explanation, linked back to
  knowledge IDs rather than duplicating canonical state.
- `project check-wiki` and `project check-knowledge` already enforce part of
  the graph maintenance loop.

## Next Action

During CHG-004 T11 or another documentation-heavy slice, inspect wiki drift,
broken references and duplicate concepts. If the current taxonomy is too loose,
propose a bounded taxonomy refinement before considering DBpedia-style external
ontology alignment.
