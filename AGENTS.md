# Repository Operating Contract

## Project identity

This repository is an AI-first project template. Agents should reduce
decision fatigue by inspecting evidence, recommending sensible defaults, and
asking only the smallest useful question set when material ambiguity remains.

The template is the source of reusable project specialisation. Generated
projects must no longer present themselves as the template after `/specialise`.

## Canonical commands

The canonical command surface is `.agentic-template/bin/project`.

| Command | Purpose |
|---|---|
| `project init` | Evidence-driven discovery, specialisation, identity rewrite and validation |
| `project inspect` | Print compact project evidence |
| `project check` | Run all repo-contract, profile, handoff, knowledge and tooling checks |
| `project repo-check` | Validate required files, skills and commands |
| `project check-profile` | Validate PROJECT_PROFILE.toon structure and resolved state |
| `project check-handoff` | Validate HANDOFF.toon |
| `project check-knowledge` | Validate knowledge entries |
| `project check-readme` | Validate README is not template-facing and has required sections |
| `project ready` | Run all applicable deterministic readiness checks |
| `project compose-config` | Validate docker compose configuration |
| `project compose-test` | Bounded Compose smoke test with deterministic cleanup |
| `project infra-check` | IaC formatting and static validation |
| `project doctor` | Diagnostic summary of checks and blockers |
| `project self-test` | Template fixture-driven integration self-test |

Unspecialised commands (`test`, `lint`, `run`, `image`, `image-test`,
`contract-test`, `integration-test`, `component-test`, `e2e-test`) fail
clearly until specialised during `/specialise`. Generated projects may mark
non-applicable commands explicitly rather than leaving them unspecialised.

## Architecture and dependency rules

- Infer from repository evidence before asking preference questions.
- Recommend the smallest sufficient architecture.
- Keep facts separate from assumptions and model inference.
- Prefer experiments over long debate where evidence is cheap.
- Use Clean Architecture principles to protect meaningful boundaries, not to
  create ceremony.
- Do not add databases, brokers, Kubernetes, cloud emulators or extra runtimes
  without evidence.
- Developer tooling is Nix-owned. Runtime packaging may use containers.
- CI must call repository commands instead of duplicating build logic.
- Do not merge provider-specific or platform-specific paths into shared
  abstractions unless explicitly requested.

## Testing expectations

- Default to test-first for meaningful behaviour.
- Use a testing trophy: strong unit/domain feedback, strong integration or
  component confidence, focused contracts, and a few high-value E2E paths.
- Select test layers from actual risks, not a fixed checklist.
- Unspecialised test targets must fail clearly and point to
  `.agentic-template/bin/project init`.
- Real dependency semantics should be tested when cheap and materially
  important. Prefer Testcontainers for lifecycle-managed integration-test
  dependencies.
- Do not leave generic test placeholders after `/specialise`.

## Container and infrastructure rules

- Every project must make an explicit container decision. Deployable services
  and web applications default to a tested application image unless evidence
  supports a documented exception.
- Libraries, mobile apps, desktop apps and Godot projects may record
  containerisation as `not_applicable` with a reason.
- `image-test` must build, start, smoke-test, stop and clean up.
- Use Compose when the runnable demonstration contains multiple services,
  when local execution requires external dependencies, or when a
  production-like integration risk is cheap and meaningful.
- Require pinned images, health checks, health-aware dependency ordering,
  named services, documented ports, no committed secrets, and deterministic
  cleanup.
- Every project must explicitly record local topology, deployment target and
  IaC status (`required`, `deferred`, or `not_applicable`).
- Never automatically apply infrastructure from generic template CI.
- Do not require cloud credentials for ordinary IaC validation where
  avoidable.

## Documentation update triggers

Update documentation when:

- project state changes from template to specialised;
- runtime, testing, container, infrastructure or CI decisions change;
- README, AGENTS, PROJECT_PROFILE or HANDOFF become stale;
- active specs are delivered, changed, deferred or removed;
- architecture boundaries or ADRs change;
- canonical commands are specialised or marked not applicable;
- delivery reconciliation is performed.

## Branch and PR workflow

- One bounded issue per branch.
- Open a PR for integration. Human or lead agent owns merge.
- Direct commits to `main` require explicit user authorisation.
- Force-push requires explicit authorisation and never targets `main`.
- CI must pass before merge.

## Worktree rules

- One bounded issue and branch per agent worktree.
- One mutable worktree per agent.
- Coordination checkout is not used for delegated implementation.
- Never remove a dirty worktree.
- Verify commit and push state before cleanup.
- Human or lead agent owns integration and merge.
- Worktrees live under `.worktrees/`.

## Agent roles and ownership

- Persistent roles are for continuing responsibility.
- Subagents are for bounded work.
- External models are workers, reviewers or consultants.
- Do not send the whole repository to every agent.
- Use `.agents/skills/CATALOG.toon` to lazy-load only relevant skills.
- Search `.agents/knowledge/` before creating new project guidance.
- Do not promote task discoveries directly to canonical knowledge without
  evidence, repetition or review.
- The project lead owns final synthesis. Do not force consensus.
- Use stronger models only where added capability is likely to matter.
- Required roles are selected by risk; do not activate every role
  automatically.

## Team and model fallback

### Agent team fallback

Fallback order:

1. Persistent agent team with bounded role ownership.
2. Independent subagents.
3. Sequential role passes.
4. Single lead agent using an explicit review checklist.

When degrading:

- record why the preferred topology was unavailable;
- preserve the same acceptance and review gates;
- update `HANDOFF.toon`;
- state which independent challenge was lost;
- do not pretend a single agent constitutes an independent team.

### Model and quota fallback

Before changing model or provider:

1. update `HANDOFF.toon`;
2. record fixed decisions;
3. record unresolved ambiguity;
4. preserve test results;
5. record branch, worktree and commit state;
6. identify safe bounded next work.

Use stronger models for ambiguity, architecture, risk and conflict. Use
midrange models for bounded implementation, testing and documentation. Use
smaller or local models for mechanical edits, command execution and metadata
maintenance.

Escalate when architecture assumptions conflict, public contracts change,
tests repeatedly fail, security or privacy risk appears, reviewers disagree,
or the task can no longer be safely bounded.

## Required state files

- `PROJECT_PROFILE.toon` records current evidence-backed understanding.
- `HANDOFF.toon` records current semantic work state, not history.
- `CUSTOMIZE_THIS_PROJECT.toon` is the bootstrap contract for new projects.
- `.agents/knowledge/` records durable reviewed knowledge and unreviewed
  proposals.
- Material unknowns must be captured in `PROJECT_PROFILE.toon` as soon as they
  matter. Unknowns do not automatically block work.

## Handoff requirements

`HANDOFF.toon` must contain:

- current objective;
- current phase;
- completed work;
- next actions;
- active assumptions and decisions;
- blocking questions;
- known risks;
- files changed;
- tests run;
- branch, worktree and commit state;
- team or model fallback state where relevant.

## Communication rules

- Put the most important conclusion first.
- Use concise sections, short paragraphs, small tables and ASCII diagrams.
- Use TOON for compact semantic state and Markdown for durable explanation.
- Prefer progressive disclosure over walls of text.

## Git provenance

Use real commit author and committer dates. Do not set `GIT_AUTHOR_DATE`,
`GIT_COMMITTER_DATE`, `--date`, system time, file mtimes, Makefiles, scripts or
CI to make new work appear to have been created earlier than it was.
