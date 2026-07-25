# Documentation Map

Start here when inspecting project guidance. Keep durable explanation in
Markdown and active state in structured files.

| Area | Path | Purpose |
|---|---|---|
| Operating contract | `AGENTS.md` | Canonical rules for agents and humans |
| Project profile | `PROJECT_PROFILE.toon` | Facts, decisions, unknowns and policy |
| Handoff | `HANDOFF.toon` | Current work state and knowledge upkeep |
| Structured data | `docs/structured-data.md` | TOON/S-expression policy |
| Validation | `docs/validation.md` | What checks prove and where to record them |
| Runbooks | `docs/runbooks/` | Optional repeatable operating procedures |
| Wiki | `docs/wiki/` | Durable project explanation |
| Decisions | `docs/decisions/` | ADRs and decision records |
| Specs | `specs/` | Capabilities and change proposals |
| Knowledge | `.agents/knowledge/` | Searchable graph of durable knowledge |
| Claude adapter | `.claude/skills/agentic-template/SKILL.md` | Thin native pointer to startup and canonical skills |
| Cursor adapter | `.cursor/rules/agentic-startup-and-skills.mdc` | Always-applied startup and skill-routing rule |

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
