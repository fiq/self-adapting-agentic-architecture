---
id: Q-001
type: question
title: First LangChain4j model provider
status: open
summary: The project needs a first live model provider choice before enabling live LangChain4j CLI execution.
owners:
  - project-lead
relates_to:
  - SYS-001
evidence:
  - CUSTOMIZE_THIS_PROJECT.toon
  - specs/changes/CHG-002-live-loop-policy/proposal.md
review_after: 2026-10-26
---

# First LangChain4j Model Provider

The user specified LangChain4j but not the first live provider. `CHG-002`
proposes OpenAI via LangChain4j's official OpenAI adapter, with provider and
model selected by explicit configuration and provider construction contained in
`adapters/langchain4j`.
