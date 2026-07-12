---
name: init
description: Orchestrate evidence-driven project discovery, specialisation, validation and identity rewrite into a delivered project.
---

# /init Orchestrator

## Outcome

Transform the repository from a generic template into a specialised, truthful,
delivered project. After `/init` the repository must no longer present itself
as the AI-first project template.

## Phases

```text
discover
  -> decide
  -> specialise
  -> rewrite top-level project identity
  -> validate
  -> update handoff
```

### 1. Discover

1. Read `AGENTS.md`.
2. Read `HANDOFF.toon`.
3. Read `PROJECT_PROFILE.toon` if present.
4. Read `CUSTOMIZE_THIS_PROJECT.toon`.
5. Inspect repository evidence with `.agentic-template/bin/inspect-project`.
6. Load `.agents/skills/CATALOG.toon`.
7. Load only relevant discovery skills.
8. Update `PROJECT_PROFILE.toon` with facts, inferences, decisions and unknowns.
9. Classify unknowns as blocking or non-blocking.
10. Re-evaluate `LICENSE`; keep MIT only if compatible with project intent,
    dependencies and copied upstream code. Remove temporarily when uncertain.

### 2. Decide

1. Recommend the smallest useful architecture with confidence and evidence.
2. Explicitly record the container decision (default to a tested application
   image for deployable services unless a documented exception applies).
3. Explicitly record the local-topology and IaC decision (status: required,
   deferred, or not_applicable with a documented reason).
4. Select test layers from actual risks, not a fixed checklist.
5. Ask only remaining high-impact questions.

### 3. Specialise

1. Invoke only relevant specialisation skills.
2. Specialise the test harness and expose applicable commands.
3. Specialise SQL migrations if relational persistence exists.
4. Generate or require a container build for deployable services; record
   `not_applicable` with a reason for libraries, mobile, desktop and Godot
   projects.
5. Specialise local Compose topology when the runnable demonstration contains
   multiple services, when local execution requires external dependencies,
   or when a production-like integration risk is cheap and meaningful.
6. Specialise CI through repository commands.
7. Make non-applicable commands explicit and recorded as such.
8. Inspect tooling, Superpowers and MCP expectations.

### 4. Rewrite top-level project identity

1. Replace `README.md` with a project-facing README (do not append to the
   template README). Required sections are listed in [`README_TEMPLATE.md`](.agentic-template/templates/README_TEMPLATE.md).
2. Replace `AGENTS.md` with a project-specific operating contract. Required
   sections are listed in [`AGENTS_TEMPLATE.md`](.agentic-template/templates/AGENTS_TEMPLATE.md).
3. Mark `CUSTOMIZE_THIS_PROJECT.toon` as consumed or historical input.
4. Ensure `PROJECT_PROFILE.toon.project.state` is not `template`.
5. Update `compose.yaml` service name and topology for the project.

### 5. Validate

1. Run `.agentic-template/bin/project check`.
2. Run `.agentic-template/bin/project ready` (or its available subset).
3. Run any specialised test commands.
4. Run container, Compose and IaC validation where applicable.
5. Run `git diff --check`.

### 6. Update handoff

1. Point `HANDOFF.toon` at real project work, not template bootstrap.
2. Record completed specialisation decisions.
3. Record the current thin slice or delivery objective.
4. Record unresolved decisions and their revisit triggers.
5. Record branch, worktree and commit state.

## Mandatory postconditions

- `PROJECT_PROFILE.toon.project.state` is not `template`.
- `README.md` is project-facing.
- `AGENTS.md` is project-facing.
- `CUSTOMIZE_THIS_PROJECT.toon` is marked as consumed or historical input.
- Runtime, testing, container, infrastructure and CI decisions are explicit.
- Applicable repository commands are specialised.
- Non-applicable commands are explicitly recorded as such.
- `HANDOFF.toon` points to real project work.

## Do not

- Load every skill.
- Ask a giant technology checklist.
- Treat `npx` as a runtime.
- Add infrastructure merely because it is available.
- Duplicate `AGENTS.md` into tool-specific files.
- Leave template onboarding instructions in the generated project README.
- Leave generic test placeholders after `/init`.
- Pretend deferred or unknown decisions are `NOT APPLICABLE`.
