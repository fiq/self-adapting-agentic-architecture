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

Prove that an assertion can fail before citing it as evidence that a gate, a
decision, an audit record or a promotion behaves correctly. Break the behaviour it
guards, confirm the test fails, restore the file unchanged, and record what was
broken. A test that passes tells you nothing until you have seen it fail for the
reason you intend.

This is scoped, not universal. It applies to a new or changed assertion about one
of those four things. It does not apply to the rest of a suite, and a mutation
that is disproportionate — a slow acceptance test, or behaviour that cannot be
broken without inventing a seam for the purpose — may be replaced by an
alternative that shows the same thing: a deliberately wrong implementation the
test rejects, or a recorded red-then-green history. Say which route was taken.

A passing mutation shows the assertion is sensitive to that one change. It does
not show the oracle is right or the semantics are the intended ones, so it does
not replace review.

Mutate the mechanism, not only the assertion. A test can pass because the thing
it describes never ran: a probe whose script was never executed scored zero from
absence rather than from measurement, and the test read green. If breaking the
code under test changes nothing, the test is not weak, it is disconnected — find
out which before trusting either.

Record the mutation alongside the test run in `HANDOFF.toon`: what was broken,
that the suite failed, and that the file was restored. A claim that a suite
passes carries no weight without it.

Watch for the assertions that most often cannot fail: one that checks a key is
present when the key is written whatever the outcome, one that pins a decision
without pinning why, one whose fixture already satisfies it, and one whose
ordering hides a last-write-wins bug. Reading does not catch these. See
`PAT-004`.

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

Use one bounded issue per branch. Open a PR for integration, as a draft as soon
as the branch has something on it rather than when the work is finished, so the
diff is reviewable while it is still cheap to redirect. Human or lead agent owns
merge. Direct commits to `main` require explicit user authorisation. Force-push
requires explicit authorisation and never targets `main`. CI must pass before
merge.

Commit in small steps as the work proceeds, not in one commit at the end. Each
commit should leave the suite green and carry one reviewable idea, with its
mutation evidence in the message where the change touches a gate, a decision, an
audit record or a promotion. A single large commit hides which change caused
which effect, which is the same defect the testing rule exists to prevent: when
the whole slice lands at once, a regression has no bisectable boundary.

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

## Rebase timing

Rebase a branch onto `main` as soon as anything else merges, not when you come to
merge it. Waiting does not avoid the conflict, it defers it to the moment you are
trying to land and makes the branch's recorded state false in the meantime.

`HANDOFF.toon` is the guaranteed conflict surface, because every branch rewrites
the same session header. Two consequences follow:

- Keep branch-local handoff edits to what the change actually needs until shortly
  before merge. A handoff rewritten early will be rewritten again after the next
  rebase.
- Rebase before recording anything that references state from `main`, such as a
  head sha, a task path, or another change's identifier. A record written against
  a stale base has nowhere correct to attach.

A branch that is behind is not merely inconvenient: its `head`, `next_actions`
and status lines describe a `main` that no longer exists, and an agent resuming
from it will act on that. Rebase, revalidate, then continue.

Force-push only with `--force-with-lease`, only on a feature branch, never on
`main`.

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

### Other agents on this machine

Delegation is real here, not aspirational: other coding agents can be driven from
the shell and used for independent review and for bounded implementation you can
fence off by file.

**Detect before you assume.** This repository is a template and runs on machines
that are not the author's. Find out what is actually installed rather than
reaching for a name from this document:

    .agentic-template/bin/project check     # reports tooling status
    command -v codex opencode claude gemini # what is on PATH
    opencode models                         # what a harness has configured

Adapt to what you find. Any agent that takes a prompt and can read a repository
can review; the value is an independent pass, not a particular vendor. If two
harnesses are available, prefer different providers for review passes over two
runs of the same one, because different providers disagree in ways one provider
twice does not. If only one is available, use it and say that the second opinion
is missing. If none is, fall back to a single lead with an explicit review
checklist and record what independent challenge was lost, which the team and model
fallback rule above already requires.

The invocations below are the author's setup, verified in an August 2026 session.
Treat them as worked examples, and check `--help` before assuming a flag.

    # independent review, cannot write anything
    codex exec --sandbox read-only --ephemeral --skip-git-repo-check \
        -o REVIEW.out "$(cat BRIEF.md)"

    # bounded implementation in its own worktree
    codex exec --sandbox workspace-write --skip-git-repo-check "$(cat BRIEF.md)"

    # another provider, for a second opinion that is not codex
    opencode models | grep glm          # list what is configured
    opencode run --model neuralwatt/glm-5.2-flex "$(cat BRIEF.md)"

Route by cost and by what the task needs. A review or a bounded refactor does not
need the strongest model; architecture, ambiguity and adjudication do. Running a
cheaper provider alongside `codex` is worth it for review specifically, because
two providers disagree in ways two runs of one provider do not.

Practical constraints learned the hard way:

- `codex exec --sandbox workspace-write` mounts the Git directory read-only, so a
  delegated agent can change files but cannot commit. Expect to commit its work
  yourself, and tell it so in the brief rather than letting it fail at the end.
- `--dangerously-bypass-approvals-and-sandbox` may be refused by the harness. Do
  not reach for it; `workspace-write` is enough.
- An `opencode` session binds to the directory it was created in.
- **Verify the brief reached the agent.** An empty or missing prompt file makes
  the agent read the startup contract, answer "what would you like to work on",
  and exit zero. That looks like a hijacked agent and is actually a shell bug in
  the caller. Check the file exists and is non-empty before blaming the tool.

### Driving them: dispatch, context, sessions, response

**Dispatch.** Write the brief to a file first and pass it as `"$(cat BRIEF.md)"`.
Do not build the brief and launch the agent in one compound shell command: if any
part is refused, the file never gets written and the agent silently receives an
empty prompt. Run the agent in the background and append a sentinel to its log
(`echo "exit=$?" >> LOG`), because the harness has reported success for a process
that failed. Poll for the sentinel, not for the harness's own status.

**Context.** Send a bounded packet, never the repository. Name the change under
review as a command the agent can run (`git diff main...HEAD`), name the two or
three context files worth reading, and state the files it owns and the files it
must not touch. Put the task first in the brief: opening with "read AGENTS.md
first" invites the agent to treat reading as the whole job. Say plainly what has
changed since anything it might remember, because a resumed session carries stale
knowledge and will act on it.

**Sessions.** `codex exec --ephemeral` leaves nothing behind and suits a one-shot
review. `opencode run` persists sessions and can resume one with `--continue` or
`--session <id>`, which is worth it when a second pass should keep the first
pass's context; `opencode export` can recover a verdict from a session whose
output looked lost. Start a fresh session, never a resumed one, when the agent's
role changes — a reviewer must not inherit an implementer's conclusions.

**Response.** The final message is the deliverable, and it is a set of claims.
Verify each finding against the code before acting on it, including the ones you
expect to be right, and reproduce at least one of its mutation proofs before
repeating any of them as evidence. Never accept "the suite passed" on trust: run
it yourself with `--rerun-tasks`, because a cached no-op prints BUILD SUCCESSFUL
in under a second and looks identical to a real run. When an agent reports a
blocker instead of working around it, that is the behaviour you want; say so and
finish the job it could not.

**Several at once.** Reviews are read-only, so they compose: run them in parallel
and consolidate. Give each a different brief aimed at ground the previous passes
did not cover, because repeated passes over one surface converge and stop finding
things. Implementations must not share a file. Give any agent a quiescent tree —
reviewing a worktree you are still editing produces findings about your edits
rather than about the change.

Give a delegated agent an explicit file fence, the testing rule, and an
instruction to report what it could not do. Then treat everything it returns as a
claim: verify its findings against the code and reproduce at least one of its
mutation proofs before repeating them as evidence. Its own tests are the weakest
part of its output, because it wrote them against its own understanding.

## Session and resource discipline

Treat a model session as bounded execution state, not as the source of truth.
Reuse one session for one stable objective, role and data-permission scope so
provider prompt caching can work. Start a fresh session when any of those
change, when independent review is required, after a transport failure, or
when the response reserve would be consumed. Record the retained session ID,
route and reset reason in `HANDOFF.toon`; the repository, spec and handoff
remain the durable context.

Token spend is a cost to manage, not a byproduct. Prefer the cheapest action that
answers the question: read the part of the file you need rather than the whole
file, search rather than enumerate, and check a fact once rather than re-deriving
it. Do not re-read what is already in context, do not restate a plan you have
already stated, and do not narrate options you are not going to take.

Delegation and review both cost real tokens, so spend them where independent
challenge actually pays: a bounded implementation another agent can do while you
work, or a review aimed at ground no earlier pass covered. A subagent that
re-derives context you already hold is the expensive way to get an answer you
could have written yourself.

Verify before investigating. A wrong diagnosis costs more than the check that
would have prevented it: confirm the file exists, the command ran, the output is
what you think it is. Most expensive debugging in this repository has been an
agent reasoning at length from a premise one command would have falsified.

Choose the cheapest model class likely to complete the bounded task reliably.
Give delegated models a compact context packet with source references rather
than the full repository. Before invoking a model, set and record an input,
output, credit, wall-clock and retry budget where the provider exposes them;
record actual tokens, cache-hit evidence, latency and cost when available, and
say `unavailable` rather than estimating an unsupported price. Cache hits are
an efficiency signal, never a correctness or approval signal.

Multi-model work assigns explicit roles (lead, bounded implementer, independent
reviewer or mechanical maintainer). A reviewer uses a clean session and does
not inherit an implementer's unreviewed conclusion. Providers and models remain
explicit adapter choices: no automatic provider selection, fallback or retry
may weaken deterministic validation, fitness, promotion, audit or privacy
constraints.

## Assessment lenses

Every assessment — a review pass, a self-review before a PR, a clean-up loop —
applies these lenses, and each one is either applied or declared not applicable.
Silently skipping a lens is what lets a whole dimension of a change go unexamined
while the write-up still reads as thorough.

- **Correctness.** Does it do what it claims, and has each new assertion about a
  gate, a decision, an audit record or a promotion been seen to fail?
- **Architecture.** Boundaries, dependency direction, coupling, reversibility.
  This one is not advisory: `check-architecture-boundaries` runs it as a fitness
  function, so a layering violation is a failed check rather than an opinion.
- **Quality and debt.** Boy-scout the path of the change; reuse at the second
  occurrence; no silent TODOs or dead code.
- **User experience**, wherever the change touches something a person operates
  or reads: CLI commands, flags and help text, console and reporter output,
  error messages, journal and report content, README and docs. The test is not
  whether it looks tidy on the happy path. It is whether a person who hits the
  failure can tell what happened and what to do next, whether the default is the
  safe path, and whether a flag's name means what it does.
- **Infrastructure quality and practice**, wherever infrastructure exists in the
  repository: CI workflows, the build, container images, Compose topology and
  infrastructure as code. Load the matching `specialise/ci`, `container-build`
  or `infra-*` skill rather than reviewing from memory.

The relevance condition is real, not an escape hatch. This repository currently
has no deployment target and an empty Compose topology, so the infrastructure
lens reaches CI and the Nix and Gradle build and nothing else; say that rather
than reporting an infrastructure review that had nothing to look at. Equally, a
change confined to `modules/domain` has no user surface, and saying so is the
honest outcome — but a change that adds a CLI flag does, and "no UX impact" would
be false.

## Independent review and consolidation

Reviews are read-only, so several reviewers may run on one change at once. They
share no mutable state, so their findings combine additively. Their conclusions
may still contradict each other, and frequently do; that is what consolidation
adjudicates, and it is a reason to run more than one reviewer rather than an
argument against it.

Read-only is an invariant to enforce, not a fact to assume. A reviewer given
write tools mutates the change the others are reading, and additivity silently
stops holding.

Implementation agents must not share a file. Prefer giving reviewers the same
change with different briefs over giving more reviewers the same brief, and aim
each brief at a region the previous passes did not examine. Repeated passes over
the same surface converge and stop finding things; a pass aimed somewhere new
does not.

Consolidating the findings is the work, not the reviewing:

- deduplicate overlapping findings;
- adjudicate contradictions rather than averaging them;
- verify every finding against the code before acting on it, including findings
  you expect to be right;
- apply the resulting fixes serially, in one place.

A single clean review is not strong evidence. Passing checks are not a review.
Do not merge on green CI alone, and do not treat a reviewer's confidence as
evidence. See `PAT-003`.

## Communication rules

Put the most important conclusion first. Use concise sections, short
paragraphs, small tables and ASCII diagrams as complexity rises. At hard
choices, attribute relevant persona stances as `discourages`, `accepts` or
`encourages`, then let the lead synthesize.

### Plain English is the default, not the fallback

Explain in plain English, with bullets and an ASCII diagram, before reaching for
the technical register. This applies to conversation and to user-facing
documents alike.

- Say what the thing *is* in ordinary words before naming it.
- Introduce a term before using it, or do not use the term.
- Prefer a bulleted list to a paragraph, and a diagram to a prose description of
  a flow, a layering or a lifecycle.
- When asking for a decision, attach a recommendation to each option and say
  plainly what you do *not* need decided.

Precision and plainness are not in tension. The failure mode is not length: it
is jargon used before it is introduced, and structure that buries the decision
inside the analysis. Keep the class names and `file:line` evidence, and put them
where they support a claim rather than where they carry it.

The same standard governs `README.md` and the documents under `docs/`. Do not
shorten for its own sake — an earlier cut removed load-bearing rationale and had
to be partly reverted.

### Rewriting a document you did not write

A clarity pass must not become a content pass. The rule is that every factual
claim in the rewrite has to be present in the original.

The characteristic failure is an invented summary sentence: a tidy
generalisation such as "each port is filled by an adapter", which reads well,
was never claimed, and is false. That exact sentence reached `docs/wiki/` in a
delegated pass and inverted the deterministic boundary, because scoring
deliberately does not live in an adapter. Reviewing prose for style does not
catch these; only checking each claim against the source does.

Where a statement is unclear and cannot be verified, keep the original wording
and say so, rather than rewriting it into something plausible.

## Handoff requirements

`HANDOFF.toon` must contain the current objective, phase, completed work, next
actions, active assumptions and decisions, blocking questions, known risks,
files changed, tests run, branch/worktree/commit state, team or model fallback
state where relevant, and knowledge consulted/proposals/no-record rationale.

## Commit message hygiene

Never put a session URL, session identifier, API key, token or any other
credential-shaped string in a commit message. This repository is public, and a
commit message cannot be redacted after the fact: rewriting history leaves the
original commits fetchable by SHA on the host, visible in pull request views, and
present in every existing clone and fork.

Attribution trailers naming a tool or a co-author are fine. Anything that
identifies a specific session or grants access to one is not.

`check-repo-contract` fails when a commit on the current branch carries one.

## Git provenance

Use real commit author and committer dates. Do not set `GIT_AUTHOR_DATE`,
`GIT_COMMITTER_DATE`, `--date`, system time, file mtimes, Makefiles, scripts or
CI to make new work appear to have been created earlier than it was.
