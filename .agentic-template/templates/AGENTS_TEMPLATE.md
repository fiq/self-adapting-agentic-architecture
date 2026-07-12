# AGENTS Template

After `/init`, replace the template `AGENTS.md` with a project-specific
operating contract. Keep this template as hidden source material under
`.agentic-template/templates/`.

## Required sections

A generated project AGENTS.md must include:

1. **Project identity** — name, purpose and primary consumer.
2. **Canonical commands** — `.agentic-template/bin/project` commands that
   apply, and explicit "not applicable" markers for non-applicable commands.
3. **Architecture and dependency rules** — runtime, framework, Clean
   Architecture boundaries, and what must not be added without evidence.
4. **Testing expectations** — test layers, test-first policy, real dependency
   semantics.
5. **Container and infrastructure rules** — container decision, Compose
   topology, IaC status.
6. **Documentation update triggers** — when README, AGENTS, PROFILE, HANDOFF,
   wiki, ADRs and specs must be updated.
7. **Branch and PR workflow** — one issue per branch, PR requirements, merge
   ownership.
8. **Worktree rules** — one mutable worktree per agent, no dirty removal,
   cleanup verification.
9. **Agent roles and ownership** — persistent roles, subagents, delegation.
10. **Team and model fallback** — degradation order and handoff protocol.
11. **Handoff requirements** — what HANDOFF.toon must contain.
12. **Git provenance** — real commit dates, no history rewriting.

## CLAUDE.md

`CLAUDE.md` must remain a symlink to `AGENTS.md`. It must not duplicate the
operating contract.

## Preserve

Preserve useful generic guardrails from the template AGENTS.md, but make them
project-specific. Do not leave the 50,000-foot view describing the repository
as "an AI-first project template".
