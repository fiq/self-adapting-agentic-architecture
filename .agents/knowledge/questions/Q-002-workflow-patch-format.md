---
id: Q-002
type: question
title: Workflow representation and patch format
status: open
summary: The first real workflow representation and mutation patch format need an explicit deterministic contract.
owners:
  - project-lead
relates_to:
  - SYS-001
risks:
  - RISK-001
evidence:
  - CUSTOMIZE_THIS_PROJECT.toon
  - PROJECT_PROFILE.toon
review_after: 2026-10-26
---

# Workflow Representation and Patch Format

The current thin slice keeps `Mutation.patch` as bounded text. The next
validation policy needs to decide whether the first executable contract is a
small TOON workflow document, a Java properties-style patch, or another
deterministic format before any AST mutation or LSP integration is added.
