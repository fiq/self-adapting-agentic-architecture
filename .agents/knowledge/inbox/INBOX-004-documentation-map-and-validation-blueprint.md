---
id: INBOX-004
type: inbox
title: Documentation map and validation blueprint for generated projects
status: proposed
summary: Template users benefit from a terse docs map, a validation blueprint and a `project docs` command that make project guidance easy to inspect without cluttering generated READMEs.
proposal_type: pattern
relates_to:
  - INBOX-002
evidence:
  - docs/README.md
  - docs/validation.md
  - .agentic-template/bin/docs-map
created_during: import vrunnable documentation patterns
recommended_action: promote to PAT if generated projects keep docs easier to navigate after specialisation
expires_after: 2026-10-26
---

# Documentation Map And Validation Blueprint

## Proposal

Generated projects should retain a terse `docs/README.md` map and
`docs/validation.md` blueprint. The README stays project-facing while the docs
map points agents and humans to deeper operating material.

## Rationale

This imports the useful part of `vrunnable`'s operational documentation style:
clear navigation, explicit validation limits and handoff recording, without
copying domain-specific VR runbooks into unrelated projects.
