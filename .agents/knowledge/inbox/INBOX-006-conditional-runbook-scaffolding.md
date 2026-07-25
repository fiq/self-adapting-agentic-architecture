---
id: INBOX-006
type: inbox
title: Conditional runbook scaffolding
status: proposed
summary: Generated projects should include runbooks only for repeated operations with exact commands, expected output, cleanup and validation recording.
proposal_type: pattern
relates_to:
  - INBOX-004
evidence:
  - docs/runbooks/README.md
  - docs/runbooks/RUNBOOK_TEMPLATE.md
  - vrunnable docs/runbooks pattern
created_during: import vrunnable documentation patterns
recommended_action: promote to PAT if generated projects use runbooks without accumulating generic provider clutter
expires_after: 2026-10-26
---

# Conditional Runbook Scaffolding

## Proposal

Keep `docs/runbooks/` available as a lightweight scaffold, but add concrete
runbooks only when the project has repeated operations such as migrations,
secrets, release, provisioning or manual validation.

## Rationale

This preserves the useful operational discipline from `vrunnable` while
avoiding generic cloud or provider instructions in specialised projects that do
not need them.
