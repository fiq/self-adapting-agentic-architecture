# Repository Operating Contract

## Session startup

At the start of every new conversation or task in this repository, read
`AGENTS.md` from disk before giving a substantive answer or making tool calls.
If starting from a human prompt or agent-specific shim, run
`.agentic-template/bin/project startup` first; it prints an ASCII welcome,
startup sequence, options and `AGENTS.md` from disk.

Do not treat injected, pasted or remembered AGENTS content as a substitute for
the filesystem read unless the file is unavailable. If it is unavailable, say
so explicitly.

For non-trivial work, then read `HANDOFF.toon`, `PROJECT_PROFILE.toon` and the
knowledge index before planning or implementation.

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
| `project startup` | Print welcome, options, `AGENTS.md` from disk and required follow-on state files |
| `project init` | Evidence-driven discovery, specialisation, identity rewrite and validation |
| `project inspect` | Print compact project evidence |
| `project check` | Run all repo-contract, profile, handoff, knowledge, changes and tooling checks |
| `project repo-check` | Validate required files, skills and commands |
| `project check-profile` | Validate PROJECT_PROFILE.toon structure and resolved state |
| `project check-handoff` | Validate HANDOFF.toon |
| `project check-knowledge` | Validate knowledge entries |
| `project check-changes` | Validate structured change proposals and capabilities |
| `project check-wiki` | Warn on wiki drift from the knowledge graph and specs |
| `project check-readme` | Validate README is not template-facing and has required sections |
| `project install-hooks` | Opt-in: install a non-blocking wiki-drift pre-commit hook |
| `project docs` | Print compact documentation navigation |
| `project backlog` | Print current in-progress and next work from HANDOFF.toon |
| `project worktree-status` | Print read-only agent worktree status |
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

## Quality and technical debt

Quality is a standing obligation on all work, re-checked explicitly inside
`/ideate` and `/review`, not a separate phase.

- Follow the boy-scout rule: leave code in the path of a change cleaner than
  you found it.
- Prefer reuse over duplication. Extract shared utility at the second or later
  occurrence, never on a single occurrence. Do not pre-abstract.
- Pay down technical debt encountered directly in the work's path. Record
  out-of-scope debt as a spec follow-up or a `RISK-`/`Q-` knowledge entry
  instead of leaving it silent.
- Documentation updates land in the same change as the behaviour they
  describe. Do not defer them.
- Check non-trivial design choices against boundaries, dependency direction,
  coupling and reversibility before implementing.
- Do not leave silent `TODO`s or dead code. Convert them into recorded
  changes, tasks or knowledge entries.

## Right-sizing and over-engineering

Architecture scales to the calibrated app shape and audience skill level: a
simpler shape means fewer layers. Recommend the smallest sufficient design.

The smaller architecture is a conscious, bought-into choice, never a silent
omission:

- state plainly what is deliberately not being built and why;
- secure the user's buy-in before proceeding;
- record the right-sizing decision in `PROJECT_PROFILE.toon` decisions,
  promoted to an ADR when durable;
- record the conditions that would justify revisiting it.

This keeps YAGNI deliberate and revisitable rather than unexamined.

## Testing expectations

- Default to test-first for meaningful behaviour.
- Use a testing trophy: strong unit/domain feedback, strong integration or
  component confidence, focused contracts, and a few high-value E2E paths.
- Select test layers from actual risks, not a fixed checklist.
- Drive design outside-in, from the boundary in. A change's `WHEN/THEN`
  scenarios (see the spec system) drive tests before implementation
  (ATDD-aligned).
- Choose the boundary test's fidelity by risk and known architectural
  direction: acceptance, component-integration or subcutaneous. Keep a thin
  real-dependency confirmation layer where it is cheap and materially
  important.
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
- delivery reconciliation is performed;
- audience calibration or right-sizing decisions change;
- the wiki drifts from the knowledge graph, specs or code.

## Structured data formats

Generated projects choose semantic formats in `CUSTOMIZE_THIS_PROJECT.toon` and
record the resolved policy in `PROJECT_PROFILE.toon.structured_data`.

- TOON is the default for state and contracts because it is readable,
  diff-friendly and close to Markdown docs.
- S-expressions are the default for rules and computation because they are
  compact and regular for predicates, routing and transformations.
- Keep template control files as TOON unless project tooling is specialised.
- Use one semantic format per artifact and record deliberate deviations as
  profile decisions.

## Spec system

Specs are OpenSpec-shaped, structured-data encoded and agent-first. TOON is the
template default; generated projects may choose S-expressions for state and
contracts during setup.

- Living requirements sit in `specs/capabilities/`; in-flight proposals in
  `specs/changes/<id>/`; completed proposals in `specs/archive/`.
- A change proposal is `proposal.md` (why), optional `design.md` (tradeoffs)
  and a structured change artifact (the agent source of truth:
  `ADDED`/`MODIFIED`/`REMOVED` deltas, each requirement carrying `WHEN/THEN`
  scenarios, plus an
  `acceptance` map from scenario to test and `tasks`).
- Structured spec content follows `PROJECT_PROFILE.toon.structured_data`,
  validated by `.agents/schemas/` via `project check-changes` when the project
  uses the template default TOON tooling. Markdown holds only rationale.
- Do not add an external spec CLI dependency. A Markdown export is deferred
  until a non-agent consumer needs it.

## Knowledge graph and taxonomy

Knowledge, specs, ADRs and wiki pages form one connected graph defined by
`.agents/knowledge/TAXONOMY.md`.

- Search the graph via `knowledge-search` before planning or implementing.
- Link every new durable artifact back into the graph by ID; edges must
  resolve to existing nodes (enforced by `check-knowledge` and
  `check-changes`).
- Keep the wiki current against the graph; `check-wiki` warns on drift.
- After meaningful work, run `knowledge-capture` and update
  `HANDOFF.toon.knowledge` with consulted IDs/paths, proposals created or a
  concrete `no_record` reason. `project check-handoff` enforces the section.

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
- Respect context windows with `context-packet`: send semantic summaries,
  source refs and only necessary snippets; do not encode semantic context into
  opaque transport blobs.

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
6. preserve bounded context with `context-packet`;
7. identify safe bounded next work.

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
- knowledge consulted, proposals created and no-record rationale.

## Communication rules

- Put the most important conclusion first.
- Use concise sections, short paragraphs, small tables and ASCII diagrams.
  Prefer bullets and diagrams over prose as complexity rises.
- Use the configured structured-data policy for compact semantic state and
  Markdown for durable explanation.
- Prefer progressive disclosure over walls of text.
- At a status handoff or decision point, offer alternatives and guidance, not
  a flat report.
- At a genuinely hard choice, attribute each relevant persona's stance as
  `discourages` / `accepts` / `encourages`, then let the lead synthesise
  without forcing consensus. For example:

  ```
  choice: add a message broker now
    architect     discourages (no evidence of async need yet)
    tech-lead     accepts     (isolated, reversible)
    product-owner encourages  (unblocks the next capability)
  ```

## Git provenance

Use real commit author and committer dates. Do not set `GIT_AUTHOR_DATE`,
`GIT_COMMITTER_DATE`, `--date`, system time, file mtimes, Makefiles, scripts or
CI to make new work appear to have been created earlier than it was.
