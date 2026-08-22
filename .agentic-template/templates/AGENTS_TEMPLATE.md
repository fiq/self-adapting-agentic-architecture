# AGENTS Template

## Session startup

At the start of every new conversation or task in this repository, read
`AGENTS.md` from disk before giving a substantive answer or making tool calls.
If starting from a human prompt or agent-specific shim, run
`.agentic-template/bin/project startup` first; it prints an ASCII welcome,
startup sequence, options and `AGENTS.md` from disk.

Do not treat injected, pasted or remembered AGENTS content as a substitute for
the filesystem read unless the file is unavailable. If it is unavailable, say
so explicitly.

For non-trivial work, then read `HANDOFF.toon`, `PROJECT_PROFILE.toon`,
`docs/context-store.md` and the knowledge index before planning or
implementation.

After `/specialise`, replace the template `AGENTS.md` with a project-specific
operating contract. Keep this template as hidden source material under
`.agentic-template/templates/`.

## Required sections

A generated project AGENTS.md must include:

1. **Session startup** — read `AGENTS.md` from disk before substantive answers
   or tool calls; expose `.agentic-template/bin/project startup` as the
   startup handshake with an ASCII welcome, boot sequence and options; for
   non-trivial work, then read `HANDOFF.toon`, `PROJECT_PROFILE.toon` and the
   knowledge index before planning or implementation.
2. **Project identity** — name, purpose and primary consumer.
3. **Canonical commands** — `.agentic-template/bin/project` commands that
   apply (including `check-changes` and `check-wiki`), and explicit "not
   applicable" markers for non-applicable commands.
4. **Architecture and dependency rules** — runtime, framework, Clean
   Architecture boundaries, and what must not be added without evidence.
5. **Quality and technical debt** — boy-scout rule, reuse over duplication at
   the 2nd+ occurrence, pay in-path debt / record out-of-scope debt, docs land
   in the same change, no silent TODOs.
6. **Right-sizing** — architecture scales to the calibrated audience; the
   smaller design is a conscious, recorded, bought-into choice.
7. **Testing expectations** — boundary-in, ATDD-aligned design; fidelity by
   risk (acceptance / component-integration / subcutaneous); real dependency
   semantics where cheap and material.
8. **Structured data formats** — configured TOON/S-expression policy; TOON
   benefits state/contracts, S-expressions benefit rules/compute.
9. **Spec system** — OpenSpec-shaped structured specs under
   `specs/capabilities` and `specs/changes`, validated by `check-changes`.
10. **Knowledge graph and taxonomy** — knowledge, specs, ADRs and wiki form one
   graph (`.agents/knowledge/TAXONOMY.md`); search before acting, link by id.
11. **Context store and fitness functions** — repo-native structure, lineage,
   behavior and conformance layers; top architecture risks become cheap
   fitness functions where practical; change handoffs include spec references,
   fitness-function deltas, validation and knowledge updates.
12. **Container and infrastructure rules** — container decision, Compose
   topology, IaC status.
13. **Documentation update triggers** — when README, AGENTS, PROFILE, HANDOFF,
    wiki, ADRs and specs must be updated.
14. **Branch and PR workflow** — one issue per branch, PR requirements, merge
    ownership.
14b. **Rebase timing** — rebase onto the integration branch as soon as anything
    else merges, not at merge time; deferring only moves the conflict to the
    moment you are trying to land, and a branch that is behind records state
    that no longer exists. Rebase before recording anything that references the
    integration branch. Force-push only with lease, only on a feature branch.
15. **Worktree rules** — one mutable worktree per agent, no dirty removal,
    cleanup verification.
16. **Agent roles and ownership** — persistent roles, subagents, delegation,
    and context-window-aware `context-packet` handoffs.
17. **Team and model fallback** — degradation order and handoff protocol.
18. **Independent review and consolidation** — reviews are read-only and may fan
    out across several actors on one change, because they share no mutable state
    and their findings combine additively; their conclusions may still
    contradict, which is what consolidation adjudicates. Read-only is an
    invariant to enforce, not a fact to assume. Implementation agents must not
    share a file. Prefer different briefs over more reviewers. Consolidation is
    the work: deduplicate, adjudicate contradictions rather than averaging them,
    verify every finding against the code before acting on it, and apply fixes
    serially. A single clean review is not strong evidence, and passing checks
    are not a review.
19. **Communication rules** — conclusion first; alternatives and per-persona
    stance (discourages / accepts / encourages) at hard choices; ASCII and
    bullets as complexity rises.
20. **Handoff requirements** — what HANDOFF.toon must contain, including
    knowledge consulted, proposals created and no-record rationale.
20b. **Commit message hygiene** — no session URLs, session identifiers, tokens or
    other credential-shaped strings in commit messages; a public commit message
    cannot be redacted after the fact, because rewriting leaves the originals
    fetchable by SHA, visible in pull request views, and present in every clone
    and fork. Tool and co-author attribution is fine.
21. **Git provenance** — real commit dates, no history rewriting.

## CLAUDE.md

`CLAUDE.md` must remain a symlink to `AGENTS.md`. It must not duplicate the
operating contract.

## Preserve

Preserve useful generic guardrails from the template AGENTS.md, but make them
project-specific. Do not leave the 50,000-foot view describing the repository
as "an AI-first project template".
