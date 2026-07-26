---
id: Q-001
type: question
title: First LangChain4j model provider
status: open
summary: The project needs a first model provider choice before implementing the LangChain4j adapter.
owners:
  - project-lead
relates_to:
  - SYS-001
evidence:
  - CUSTOMIZE_THIS_PROJECT.toon
review_after: 2026-10-26
---

# First LangChain4j Model Provider

The user specified LangChain4j but not the first provider. The current
architecture keeps this non-blocking by defining provider-neutral ports and
deferring provider-specific adapter configuration.
