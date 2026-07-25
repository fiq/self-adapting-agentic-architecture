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
7. **Structured data formats** — configured TOON/S-expression policy; TOON
   benefits state/contracts, S-expressions benefit rules/compute.
8. **Spec system** — OpenSpec-shaped structured specs under
   `specs/capabilities` and `specs/changes`, validated by `check-changes`.
9. **Knowledge graph and taxonomy** — knowledge, specs, ADRs and wiki form one
   graph (`.agents/knowledge/TAXONOMY.md`); search before acting, link by id.
10. **Container and infrastructure rules** — container decision, Compose
   topology, IaC status.
11. **Documentation update triggers** — when README, AGENTS, PROFILE, HANDOFF,
    wiki, ADRs and specs must be updated.
12. **Branch and PR workflow** — one issue per branch, PR requirements, merge
    ownership.
13. **Worktree rules** — one mutable worktree per agent, no dirty removal,
    cleanup verification.
14. **Agent roles and ownership** — persistent roles, subagents, delegation,
    and context-window-aware `context-packet` handoffs.
15. **Team and model fallback** — degradation order and handoff protocol.
16. **Communication rules** — conclusion first; alternatives and per-persona
    stance (discourages / accepts / encourages) at hard choices; ASCII and
    bullets as complexity rises.
17. **Handoff requirements** — what HANDOFF.toon must contain, including
    knowledge consulted, proposals created and no-record rationale.
18. **Git provenance** — real commit dates, no history rewriting.

## CLAUDE.md

`CLAUDE.md` must remain a symlink to `AGENTS.md`. It must not duplicate the
operating contract.

## Preserve

Preserve useful generic guardrails from the template AGENTS.md, but make them
project-specific. Do not leave the 50,000-foot view describing the repository
as "an AI-first project template".
