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
| Wiki | `docs/wiki/` | Durable project explanation |
| Decisions | `docs/decisions/` | ADRs and decision records |
| Specs | `specs/` | Capabilities and change proposals |
| Knowledge | `.agents/knowledge/` | Searchable graph of durable knowledge |

## Navigation Rules

- Search `.agents/knowledge/index.md` before planning or implementation.
- Use `docs/wiki/index.md` for human-facing durable docs.
- Use `project docs` for a command-line map of key files.
- Keep generated-project README files project-facing; do not dump template
  internals there unless they help the project user.
