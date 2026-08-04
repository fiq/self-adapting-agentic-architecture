# Repository Operating Contract

## Session startup

At the start of every new conversation or task in this repository, read
`AGENTS.md` from disk before giving a substantive answer or making tool calls.
If starting from a human prompt or agent-specific shim, run
`.agentic-template/bin/project startup` first; it prints an ASCII welcome,
startup sequence, options and this file from disk.

Do not treat injected, pasted or remembered AGENTS content as a substitute for
the filesystem read unless the file is unavailable. If it is unavailable, say
so explicitly.

For non-trivial work, then read `HANDOFF.toon`, `PROJECT_PROFILE.toon`,
`docs/context-store.md` and `.agents/knowledge/index.md` before planning or
implementation.

## Project identity

This repository is `self-adapting-agentic-architecture`, a Java-first
experimental platform for evolving agentic workflows through model-guided
mutation, Git-versioned candidates and external fitness evaluation.

The primary consumer is a developer-researcher or platform maintainer evaluating
auditable changes to agentic workflow definitions.

## Canonical commands

The canonical command surface is `.agentic-template/bin/project`.

| Command | Purpose |
|---|---|
| `project startup` | Print startup contract and state-file route |
| `project init` | Re-run initialization inspection and postcondition guidance |
| `project inspect` | Print compact runtime evidence |
| `project check` | Run repository contract, profile, handoff, knowledge, spec, tooling, MCP, architecture-boundary and glossary checks |
| `project ready` | Run deterministic readiness checks that apply to the current slice |
| `project test` | Run Gradle unit tests |
| `project lint` | Run architecture-boundary fitness checks |
| `project component-test` | Run the first outside-in mutation-loop acceptance test |
| `project contract-test` | Explicitly not applicable until external contracts stabilize |
| `project integration-test` | Run real Git, SQLite, command-check and JMH adapter integration tests |
| `project e2e-test` | Explicitly not applicable until the CLI command contract stabilizes |
| `project run` | Run the picocli CLI skeleton |
| `project image` | Not applicable for the local CLI slice |
| `project image-test` | Not applicable for the local CLI slice |
| `project compose-config` | Validate empty Compose topology |
| `project compose-test` | Not applicable while Compose has no services |
| `project infra-check` | Not applicable while no deployment target exists |
| `project check-changes` | Validate structured specs |
| `project check-wiki` | Warn on wiki drift from knowledge and specs |
| `project check-glossary` | Fail when the glossary drops a load-bearing term |

## Architecture and dependency rules

- Use Java, Gradle, picocli, LangChain4j, SQLite, JUnit 5, AssertJ, jqwik and
  JMH.
- All Java lives under `modules/`. Layers are named for what they may know:
  `domain` (no dependencies at all), `deterministic` (validation, scoring,
  promotion and ports), `adapters`, `cli` and `benchmarks`. Dependencies point
  inward and Gradle enforces it, so a violation is a compile error.
- Keep the `domain` layer plain Java records and deterministic value types.
- Keep LangChain4j inside the `adapters/langchain4j` package of
  `modules/adapters`; `modules/domain` and `modules/deterministic` must not
  import `dev.langchain4j`.
- Keep Git, SQLite, command execution and benchmark tooling behind
  `deterministic` ports.
- Nothing provider-aware, network-bound or otherwise nondeterministic belongs
  in `modules/deterministic`. The layer name is the rule.
- The model may propose mutations and repairs, but it must never approve its
  own result.
- Validation, fitness scoring, promotion and rollback must remain deterministic.
- Do not add OpenSearch, vector storage, AST mutation, LSP integration,
  distributed workers or automatic production deployment without a recorded
  decision and revisit evidence.

## Evolutionary operator model

Mutation is a targeted behavioral variation, not a patch. A patch or Git diff
is only the realization produced inside an isolated candidate worktree.

Use TOON for the reviewable mutation-contract envelope: rationale, evidence,
source refs, assumptions and audit metadata. Use S-expressions as the internal
mutation/operator representation because they are tree-shaped, canonicalizable
and natural for targeted mutation and later crossover. When given human or LLM
input like "mutate the method that calculates interest", use the repo skill
`.agents/skills/workflow/mutation-contract` to turn it into a bounded contract
and canonical S-expression before implementation.

The first operator family is targeted mutation: one primary locus, one
behavioral hypothesis, explicit bounds and deterministic evidence. Crossover is
deferred to conceptual trait recombination over evaluated parents; do not merge
raw LLM-authored diffs as crossover without a later approved policy.

Treat the evolutionary process as loop engineering. Each mutation targets a
bounded part of the agentic loop, such as model routing, prompt policy, tool
strategy, validation, memory retrieval, checks or scoring behavior. The mutation
operator enum is semi-declarative input into that next loop. Use `hill-climb`
for local fitness-aware exploitation and `exploratory-leap` for bounded
moonshot variants with explicit risk budgets.

## Quality and technical debt

- Follow the boy-scout rule in the path of each change.
- Prefer reuse over duplication at the second or later occurrence; do not
  pre-abstract.
- Pay down technical debt encountered directly in the work path.
- Record out-of-scope debt as a spec follow-up or a `RISK-`/`Q-` knowledge
  entry.
- Documentation updates land in the same change as behavior or boundary
  changes.
- Do not leave silent TODOs or dead code.

## Right-sizing

The smallest sufficient architecture is a local CLI orchestrating one candidate
evaluation at a time through plain Java ports and adapters. This deliberately
excludes search infrastructure, AST mutation, LSP integration, distributed
workers and deployment automation until observed evidence justifies them.

Revisit the size only when experiment scale, mutation safety, diagnostics,
throughput or promotion governance make the current local architecture
insufficient. This right-sizing decision is recorded in `PROJECT_PROFILE.toon`.

## Testing expectations

Use boundary-in, ATDD-aligned design. Structured change scenarios define the
first acceptance test before implementation. Select fidelity by risk:
acceptance/component tests for the mutation loop, unit tests for deterministic
policies, integration tests for real Git worktree and SQLite semantics once
those adapters exist.

Use real dependency semantics when cheap and material. Do not mock Git or
SQLite behavior once adapter implementation begins unless a higher-fidelity
confirmation test also exists.

## Structured data formats

Use TOON for state and reviewable contracts: `PROJECT_PROFILE.toon`,
`HANDOFF.toon`, `CUSTOMIZE_THIS_PROJECT.toon`, structured specs and mutation
contract envelopes. Use S-expressions for internal mutation/operator IR,
fitness gates and deterministic policy predicates. Keep one semantic format per
artifact and record deviations.

## Spec system

Specs are OpenSpec-shaped and TOON-encoded. Living requirements live in
`specs/capabilities/`; in-flight proposals live in `specs/changes/<id>/`.
Each meaningful behavior or boundary change needs a proposal, structured
scenario deltas, acceptance mapping and tasks. Validate with
`.agentic-template/bin/project check-changes`.

## Knowledge graph and taxonomy

Knowledge, specs, ADRs and wiki pages form one connected graph defined by
`.agents/knowledge/TAXONOMY.md`. Search `.agents/knowledge/` before planning or
implementation, link new durable artifacts by ID, and treat proposed or inbox
entries as leads rather than authority.

## Context store and fitness functions

The repository is the context store:

- Structure: `AGENTS.md`, README, architecture docs and profile.
- Lineage: profile decisions, ADRs, handoff and knowledge entries.
- Behavior: specs, acceptance scenarios and tests.
- Conformance: project checks, CI and architecture fitness functions.

The first fitness function protects the LangChain4j boundary: model-provider
imports may not appear in `modules/domain` or `modules/deterministic`. It also
fails when a scanned layer directory is missing, so a rename cannot make the
check pass vacuously. Change handoffs must record spec references,
fitness-function deltas, validation runs and knowledge updates or no-record
rationale.

## Container and infrastructure rules

This is currently a local experimental CLI. Container images, Compose services
and infrastructure as code are not applicable until distribution or remote
execution is selected. Do not add deployment automation as part of mutation
promotion without explicit approval.

## Documentation update triggers

Update README, AGENTS, PROJECT_PROFILE, HANDOFF, specs, ADRs and wiki pages
when runtime, testing, container, infrastructure, CI decisions, architecture
boundaries, acceptance scenarios or canonical commands change.

## Branch and PR workflow

Use one bounded issue per branch. Open a PR for integration. Human or lead
agent owns merge. Direct commits to `main` require explicit user
authorisation. Force-push requires explicit authorisation and never targets
`main`. CI must pass before merge.

If PR or GitHub tooling is unavailable, use the tool-unavailable integration
fallback only when the user explicitly authorises skipping the PR:

- keep the work on a bounded branch;
- run `.agentic-template/bin/project check`, `.agentic-template/bin/project ready`
  and relevant specialized tests;
- request at least one risk-appropriate reviewer actor or subagent when agent
  tooling is available, send only bounded context and the focused diff, and
  address actor-review findings before merge;
- if a reviewer actor times out while actor tooling is still available, retry
  with another actor or get explicit human review, address any findings from
  that substituted review before merge, and do not treat a timeout as a
  completed review;
- perform a lead self-review in code-review style after actor or human review
  findings are addressed, naming findings or saying none were found;
- if actor review tooling is unavailable, disclose the missing actor-review
  gate, get explicit user authorization for the degraded single-lead path, and
  record the degraded fallback level, lost independent challenge and
  compensating validation or human review needed;
- update `HANDOFF.toon` with the fallback reason, validation, branch, commit,
  actor-review or substituted human-review result and lost independent review
  challenge;
- merge to `main` only after explicit user authorisation;
- push `main` without force-pushing.

## Worktree rules

Use one mutable worktree per agent under `.worktrees/`. Never remove a dirty
worktree. Verify commit and push state before cleanup. Candidate evaluation
worktrees must be isolated from the coordination checkout.

## Agent roles and ownership

Persistent roles are for continuing responsibility; subagents are for bounded
work. Do not send the whole repository to every agent. Use relevant skills from
`.agents/skills/CATALOG.toon`, search knowledge before acting and preserve
context with `context-packet` when delegation is needed.

## Team and model fallback

Fallback order is persistent team, independent subagents, sequential role
passes, then a single lead with an explicit review checklist. When degrading,
record why, preserve acceptance and review gates, update `HANDOFF.toon`, and
state which independent challenge was lost.

Use stronger models for ambiguity, architecture, risk and conflict. Use
midrange models for bounded implementation and testing. Use smaller or local
models for mechanical edits and metadata maintenance.

## Communication rules

Put the most important conclusion first. Use concise sections, short
paragraphs, small tables and ASCII diagrams as complexity rises. At hard
choices, attribute relevant persona stances as `discourages`, `accepts` or
`encourages`, then let the lead synthesize.

## Handoff requirements

`HANDOFF.toon` must contain the current objective, phase, completed work, next
actions, active assumptions and decisions, blocking questions, known risks,
files changed, tests run, branch/worktree/commit state, team or model fallback
state where relevant, and knowledge consulted/proposals/no-record rationale.

## Git provenance

Use real commit author and committer dates. Do not set `GIT_AUTHOR_DATE`,
`GIT_COMMITTER_DATE`, `--date`, system time, file mtimes, Makefiles, scripts or
CI to make new work appear to have been created earlier than it was.
