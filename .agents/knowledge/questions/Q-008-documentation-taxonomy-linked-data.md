---
id: Q-008
type: question
status: open
title: Documentation taxonomy and linked data shape
summary: Decide whether the growing Markdown knowledge graph needs a stronger ontology or DBpedia-style linked-data conventions.
context: The project is accumulating specs, ADRs, knowledge nodes and wiki pages. Without a meaningful taxonomy, humans may experience the docs as unstructured AI-generated prose rather than a professional graph of durable concepts.
relates_to:
  - ADR-0003
  - SYS-001
owner: architect
blocking: false
discovered_during: PR #10 review and product-direction discussion
next_action: Keep the local taxonomy authoritative for now; revisit after several more Markdown-producing slices or when wiki drift warnings become noisy.
resolution: null
answered_by: []
evidence:
  - .agents/knowledge/TAXONOMY.md
  - docs/wiki/development.md
  - docs/wiki/glossary.md
---

# Documentation Taxonomy and Linked Data Shape

## Context

The repository already treats knowledge, specs, ADRs and wiki pages as a graph
through `.agents/knowledge/TAXONOMY.md`. As more Markdown lands, the project
needs enough ontology to stay navigable and professional without adopting a
large external vocabulary too early.

## Current Evidence

- The local taxonomy defines node categories and edge names.
- `project check-wiki` already warns when wiki pages drift behind knowledge or
  spec updates.
- The new smart-bridge direction adds product concepts that should be named
  consistently across ADRs, specs and wiki pages.

## Next Action

Keep the current local taxonomy authoritative. Add glossary terms only when
they appear across multiple durable artifacts. Revisit a DBpedia-style or RDF
shape if the local graph becomes too hard to traverse with Markdown links and
TOON frontmatter alone.
