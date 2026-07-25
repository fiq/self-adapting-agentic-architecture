---
id: INBOX-007
type: inbox
title: Session startup disk-read contract
status: proposed
summary: Agent startup instructions should require reading AGENTS.md from disk, provide a welcoming startup command with options, and be mirrored in generated templates and agent-specific entrypoints where available.
proposal_type: pattern
relates_to:
  - INBOX-002
  - INBOX-004
evidence:
  - user report that an agent failed to read AGENTS.md in a generated project
  - AGENTS.md
  - .agentic-template/templates/AGENTS_TEMPLATE.md
  - .github/copilot-instructions.md
  - .codex/README.md
  - .claude/README.md
  - .claude/skills/agentic-template/SKILL.md
  - .cursor/rules/agentic-startup-and-skills.mdc
  - .agentic-template/bin/startup
  - .agentic-template/bin/check-repo-contract
created_during: make AGENTS disk-read startup rule first-class
recommended_action: promote to PAT if generated projects show fewer startup contract misses
expires_after: 2026-10-26
---

# Session Startup Disk-Read Contract

## Proposal

Agent operating contracts should put `## Session startup` before project
identity and explicitly require reading `AGENTS.md` from disk before
substantive answers or tool calls. Repositories should also expose
`.agentic-template/bin/project startup` as a concrete first command that prints
`AGENTS.md` from disk. The command should give fresh sessions enough context
to understand what will happen next and which repository command to choose.

Where an agent has a separate startup surface, such as Claude skills, Cursor
rules, Copilot instructions or Codex docs, that surface should repeat the
disk-read requirement and point back to the canonical skill catalog. Repository
checks should fail when the live contract, generated contract template or
adapter files lose the startup section or weaken the disk-read language.

## Evidence

- A generated project exposed that relying on a buried onboarding sentence did
  not reliably cause agents to read `AGENTS.md`.
- `check-repo-contract` can enforce the presence and placement of the rule,
  even though it cannot prove live session behavior.
- Native tool adapters improve discovery without duplicating the canonical
  `.agents/skills/` tree.

## Curator Notes

- This is a mitigation, not a guarantee. No repository file can compel a
  noncompliant agent that ignores all repository instructions.
