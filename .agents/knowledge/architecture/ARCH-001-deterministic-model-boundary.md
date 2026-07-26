---
id: ARCH-001
type: architecture
title: Deterministic model boundary
status: proposed
summary: LangChain4j-backed model capabilities are isolated behind adapters, while validation, scoring, promotion and rollback remain deterministic Java decisions.
owners:
  - architect
relates_to:
  - DOM-001
  - SYS-001
risks:
  - RISK-001
evidence:
  - AGENTS.md
  - docs/architecture/module-boundaries.md
  - .agentic-template/bin/check-architecture-boundaries
review_after: 2026-10-26
---

# Deterministic Model Boundary

LangChain4j belongs in `adapters/langchain4j`. Core and application code define
ports and deterministic policies. The model can propose or repair a mutation,
but it cannot validate, score, promote or roll back its own result.
