# AGENTS Template

After `/specialise`, replace the template `AGENTS.md` with a project-specific
operating contract. Keep this template as hidden source material under
`.agentic-template/templates/`.

## Required sections

A generated project AGENTS.md must include:

1. **Project identity** — name, purpose and primary consumer.
2. **Canonical commands** — `.agentic-template/bin/project` commands that
   apply (including `check-changes` and `check-wiki`), and explicit "not
   applicable" markers for non-applicable commands.
3. **Architecture and dependency rules** — runtime, framework, Clean
   Architecture boundaries, and what must not be added without evidence.
4. **Quality and technical debt** — boy-scout rule, reuse over duplication at
   the 2nd+ occurrence, pay in-path debt / record out-of-scope debt, docs land
   in the same change, no silent TODOs.
5. **Right-sizing** — architecture scales to the calibrated audience; the
   smaller design is a conscious, recorded, bought-into choice.
6. **Testing expectations** — boundary-in, ATDD-aligned design; fidelity by
   risk (acceptance / component-integration / subcutaneous); real dependency
   semantics where cheap and material.
7. **Spec system** — OpenSpec-shaped, TOON-encoded specs under
   `specs/capabilities` and `specs/changes`, validated by `check-changes`.
8. **Knowledge graph and taxonomy** — knowledge, specs, ADRs and wiki form one
   graph (`.agents/knowledge/TAXONOMY.md`); search before acting, link by id.
9. **Container and infrastructure rules** — container decision, Compose
   topology, IaC status.
10. **Documentation update triggers** — when README, AGENTS, PROFILE, HANDOFF,
    wiki, ADRs and specs must be updated.
11. **Branch and PR workflow** — one issue per branch, PR requirements, merge
    ownership.
12. **Worktree rules** — one mutable worktree per agent, no dirty removal,
    cleanup verification.
13. **Agent roles and ownership** — persistent roles, subagents, delegation.
14. **Team and model fallback** — degradation order and handoff protocol.
15. **Communication rules** — conclusion first; alternatives and per-persona
    stance (discourages / accepts / encourages) at hard choices; ASCII and
    bullets as complexity rises.
16. **Handoff requirements** — what HANDOFF.toon must contain.
17. **Git provenance** — real commit dates, no history rewriting.

## CLAUDE.md

`CLAUDE.md` must remain a symlink to `AGENTS.md`. It must not duplicate the
operating contract.

## Preserve

Preserve useful generic guardrails from the template AGENTS.md, but make them
project-specific. Do not leave the 50,000-foot view describing the repository
as "an AI-first project template".
