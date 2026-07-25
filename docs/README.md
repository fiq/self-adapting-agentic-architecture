# Documentation Map

Start here when inspecting project guidance. Keep durable explanation in
Markdown and active state in structured files.

## Start Paths

| Intent | Start With | Then Read |
|---|---|---|
| Create a project from the template | `.agentic-template/bin/project startup` | `CUSTOMIZE_THIS_PROJECT.toon`, then `.agentic-template/bin/project init` |
| Continue active work | `.agentic-template/bin/project startup` | `HANDOFF.toon`, then `.agentic-template/bin/project backlog` |
| Understand the rules | `AGENTS.md` | `PROJECT_PROFILE.toon`, then `.agents/knowledge/index.md` |
| Plan or implement a change | `.agents/knowledge/index.md` | `specs/README.md`, then the relevant skill in `.agents/skills/CATALOG.toon` |
| Validate or hand off | `docs/validation.md` | `.agentic-template/bin/project check`, `.agentic-template/bin/project ready`, then `HANDOFF.toon` |

Run `.agentic-template/bin/project docs` for the same navigation as terminal
output.

## Map

### Start Here

| Area | Path | Purpose |
|---|---|---|
| README | `README.md` | First human-facing overview and onboarding |
| Startup command | `.agentic-template/bin/startup` | Agent welcome, options and `AGENTS.md` disk read |
| Docs home | `docs/README.md` | Information architecture and navigation |

### Current State

| Area | Path | Purpose |
|---|---|---|
| Operating contract | `AGENTS.md` | Canonical rules for agents and humans |
| Project profile | `PROJECT_PROFILE.toon` | Facts, decisions, unknowns and policy |
| Handoff | `HANDOFF.toon` | Current work state and knowledge upkeep |
| Customisation input | `CUSTOMIZE_THIS_PROJECT.toon` | Bootstrap intent and constraints |

### Delivery Workflow

| Area | Path | Purpose |
|---|---|---|
| Development | `docs/wiki/development.md` | Lifecycle and command sequence |
| Specs | `specs/README.md` | Capabilities and change proposals |
| Validation | `docs/validation.md` | What checks prove and where to record them |
| Runbooks | `docs/runbooks/` | Optional repeatable operating procedures |

### Knowledge And Decisions

| Area | Path | Purpose |
|---|---|---|
| Knowledge | `.agents/knowledge/` | Searchable graph of durable knowledge |
| Wiki | `docs/wiki/` | Human-facing durable docs |
| Decisions | `docs/decisions/` | ADRs and decision records |
| Structured data | `docs/structured-data.md` | TOON/S-expression policy |

### Agent Adapters

| Area | Path | Purpose |
|---|---|---|
| Claude adapter | `.claude/skills/agentic-template/SKILL.md` | Thin native pointer to startup and canonical skills |
| Cursor adapter | `.cursor/rules/agentic-startup-and-skills.mdc` | Always-applied startup and skill-routing rule |
| Copilot instructions | `.github/copilot-instructions.md` | GitHub Copilot startup shim |
| Codex instructions | `.codex/README.md` | Codex startup shim |

## Navigation Rules

- For a fresh agent session, run `.agentic-template/bin/project startup` to see
  the welcome, startup sequence, options and canonical contract before planning
  or implementation.
- Search `.agents/knowledge/index.md` before planning or implementation.
- Use `docs/wiki/index.md` for human-facing durable docs.
- Use `project docs` for a command-line map of key files.
- Add runbooks only for repeated operations with exact commands and validation.
- Keep generated-project README files project-facing; do not dump template
  internals there unless they help the project user.
