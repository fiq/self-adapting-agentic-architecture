---
name: wiki-tidy
description: Reconcile the wiki against the knowledge graph, specs and code; prune stale, keep links resolving.
---

# Wiki Tidy

## Outcome

Keep `docs/wiki/` current and connected: durable explanation that tracks the
knowledge graph, specs and code. Run at natural task boundaries and before
delivery; `project check-wiki` warns when it is due.

## Procedure

```
project check-wiki ──► drift warnings
   ▼ reconcile each wiki page against knowledge, specs and code
   ▼ prune stale content; keep the most useful explanation first
   ▼ fix links: every knowledge id referenced must resolve (TAXONOMY.md)
   ▼ add missing pages for new capabilities or systems
   ▼ re-run project check-wiki ──► clean
```

## Rules

- Wiki is Markdown (durable explanation); do not duplicate TOON state or
  canonical knowledge — link to it by id.
- Every knowledge id referenced in the wiki must resolve to a real node.
- Reflect delivered changes and new capabilities; remove content for removed
  behaviour.
- Keep pages short and progressively disclosed.

## Do not

- Turn the wiki into a change log or copy of `HANDOFF.toon`.
- Leave broken knowledge-id references.
- Expand scope beyond reconciling documentation.
