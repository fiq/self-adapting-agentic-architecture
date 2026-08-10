# CHG-004 Design — first vertical slice with classical scorer guards

This slice is the first application of `ADR-0002`'s vertical-slice delivery
pattern: one change ships something real at Layer 1, Layer 2 and Layer 3
together, plus the classical evolutionary-computing guards the ADR calls out
as prerequisites for later scorer-as-target work.

## Intent, in one sentence

Make the loop work with a live model at L1, drivable from an outer agent at
L2, against a real Java file at L3, and put the guardrails in that stop the
next scorer-touching slice from silently decaying.

## Layer-by-layer shape

### Layer 1 — live proposer against an OpenAI-compatible endpoint

The proposer is a new `OpenAiCompatibleMutationProposer` behind the existing
`MutationProposer` port. It calls LangChain4j's `OpenAiChatModel` with a
configurable `baseUrl`, so the same adapter reaches OpenAI, NeuralWatt,
together.ai, groq, Ollama's `/v1` shim and anything else that speaks the
OpenAI Chat Completions API. Configuration is via three environment
variables:

| Variable | Purpose |
|---|---|
| `SAAA_MODEL_BASE_URL` | Endpoint, e.g. `https://api.openai.com/v1` or a NeuralWatt URL |
| `SAAA_MODEL_API_KEY` | Credential; never emitted in responses or logs |
| `SAAA_MODEL_NAME` | Model id, e.g. `gpt-5.1` or a NeuralWatt model id |

The proposer builds one prompt from the baseline `WorkflowGraph`, receives
one reply, and produces one `Mutation` whose `patch` is the whole new file
content. Anything else the model emits — extra prose, alternatives, multiple
files — is rejected before validation. The proposer never sees scorer or
gate state.

Registration is a single new entry in `ProposerProfileRegistry` so
`--profile openai-compatible` works alongside the existing `fixture`
profile.

### Layer 2 — `evolve` as an MCP tool

A new `mcp` package **inside `modules/adapters`** exposes `evolve` as an MCP
tool over stdio. Its placement follows the `invariant_named_module_layout`
decision recorded in `PROJECT_PROFILE.toon`: all adapters live inside
`modules/adapters/`, and the MCP module is an adapter to the MCP protocol
just as the git and langchain4j modules are adapters to their respective
concerns.

It wraps the CLI rather than re-implementing the loop, so there is one code
path from proposal to decision. Invocation is **in-process** via the picocli
`MutationLoopCli` command class, not a subprocess, so a JVM is not started
per MCP call.

Input schema mirrors CLI flags. Output schema is a stable JSON serialisation
of `FitnessResult`:

```
{
  "candidate": { "id": "...", "commitSha": "..." },
  "objectives": {
    "subject.objective.task_success": 1.0,
    "subject.objective.reliability": 1.0,
    "subject.objective.cost_latency_budget": 1.0,
    "subject.objective.behavioral_safety": 1.0,
    "subject.objective.parsimony": 0.9,
    "subject.invariant.deterministic_checks": 1.0,
    "subject.invariant.required_behavior_cases": 1.0,
    "subject.invariant.required_objective_scores": 1.0,
    "subject.invariant.non_empty_realization": 1.0
  },
  "aggregateScore": 0.99,
  "aggregateScoreDisplay": "0.99",
  "decision": "PROMOTE",
  "journalPath": "/absolute/path/to/target/journal.md"
}
```

`aggregateScore` is the raw `double` so outer agents can compare candidates
numerically; `aggregateScoreDisplay` is the same two-decimal rounding the CLI
emits, so a human reading the tool response and a human reading the CLI
output see the same number. `journalPath` is absolute so an outer agent
process with a different cwd can still read it.

Three invariants ride on the wire:

1. **Ordering.** Hard-gate outcomes come *after* measured objective scores in
   the serialised map, mirroring `FitnessResult.objectives`. Evidence content
   cannot overwrite a recorded gate result in the serialised form either.
2. **Unidirectional.** No MCP input can force a promotion, override a gate,
   enable an auto-merge, or carry credentials. Unrecognised input keys are a
   schema violation, not a permissive pass-through. This is where the ADR-0002
   rule ("outer loop plans, inner loop scores") is enforced at the wire.
3. **Credentials pass through the environment, not the arguments.** The MCP
   server reads `SAAA_MODEL_API_KEY` from its own environment. The outer
   agent is responsible for setting that environment when it spawns the MCP
   server; the tool schema refuses to accept a credential-shaped input.
   `S2b` and `T6` together enforce this.

### Layer 3 — `AuthorityLanguage.java` as target, existing tests as gate

`modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/AuthorityLanguage.java`
is small, well-tested via `BoundedMutationValidator`, and mutating it does
not affect the scorer's own logic. That is deliberate: it exercises real code
without opening the recursive can of worms.

The behaviour case is a shell script that runs
`./gradlew :deterministic:test --tests '*BoundedMutationValidator*'` inside
the candidate worktree. Blast-radius constraints from ADR-0002 are enforced
by test rather than by convention (`S9`, `S10`, `T10`, `T10b`):

- one target file per candidate;
- the candidate worktree is discardable;
- no auto-merge to `main` under any score — enforced at the type/contract
  level, not at run time; the CLI has no such flag and the MCP schema has
  no such input;
- promotion produces a branch pointer under `refs/heads/candidate/*`; a
  merge is a separate action taken by a human;
- the `journal.md` writes into the target folder, but the SAAA repository's
  `.gitignore` covers that path so a run inside this repository never
  produces an accidental commit; the acceptance test asserts a clean working
  tree after the run so a regression that stops the gitignore matching is
  caught.

A mutation that fails to compile becomes a failed check with the compiler
output as the check summary, so the loop records a `DISCARD` rather than
crashing. Without that, the first non-compiling proposal from the live model
would turn into a stack trace instead of an ordinary evolutionary outcome.
`T9` is independently valuable for any target that could fail to compile,
not only Layer 3.

## Classical scorer guards (in this slice)

The staged hybrid from the design conversation makes safe scorer-as-target
work possible in a *later* slice. This slice adds the prerequisites now.

### Property-based tests over `PhenotypeFitnessScorer`

jqwik (already in the stack) generates arbitrary `PhenotypeEvidence` and
asserts invariants over `PhenotypeFitnessScorer.score`. The invariants
capture *intent*, not specific cases, so a mutation cannot accidentally
satisfy them while breaking meaning:

1. **Failed check → DISCARD.** For any evidence where at least one deterministic
   check is `FAILED`, the decision is `DISCARD`.
2. **Missing declared case → DISCARD.** For any evidence where a declared
   behaviour case has no evidence entry, the decision is `DISCARD`.
3. **Gate closure.** For any assignment of objective scores in `[0, 1]`, if
   any hard gate is failing, the decision is `DISCARD` regardless of the
   weighted sum.
4. **Audit-trail immutability.** Gate outcomes in the returned
   `FitnessResult.objectives` cannot be overwritten by keys present in the
   input `PhenotypeEvidence.objectiveScores`.
5. **Parsimony monotonicity.** With every other input held constant, a
   strictly smaller realization can never score `subject.objective.parsimony` worse than a
   larger one. A scorer mutation that inverts the sign of the parsimony
   formula would pass invariants 1–4 but fail this one.
6. **Task-success monotonicity.** With every other input held constant, a
   strictly larger set of passing behaviour cases can never score
   `subject.objective.task_success` worse.
7. **Threshold boundary.** A candidate whose raw weighted sum is exactly
   `PROMOTION_THRESHOLD` promotes; a candidate whose raw sum is strictly
   less discards. Regardless of how the reported score rounds.
8. **Decision derives from the raw sum, not the rounded one.** A scorer
   mutation that moved the comparison onto `aggregateScore` (the rounded
   display value) would silently shift the PROMOTE boundary by up to the
   rounding step. This property catches that.

These are the invariants a scorer-as-target slice would rely on. Encoding
them now means a future scorer mutation that weakens gating logic in a way
the existing example-based tests do not cover is still likely to break a
property, which is exactly the failure mode Option B was risky about.

Seeded for reproducibility. If a property finds a counterexample, that
counterexample is checked in as a regression fixture at the same time as the
fix.

### Golden-verdict corpus

Immutable fixtures at `modules/deterministic/src/test/resources/golden-corpus/`,
each a `(EvaluationEvidence, RealizationSummary, expected decision)` triple.
`PhenotypeGoldenVerdictCorpusTest` iterates them and asserts
`PhenotypeBridgeScorer` reproduces every recorded decision.

Coverage floor for this slice:

- at least one entry per hard gate demonstrating the gate firing;
- at least two `PROMOTE` entries with different objective profiles;
- at least one entry that would `PROMOTE` on task_success alone but is
  rescued from over-promotion by parsimony or the non-empty-realization
  gate;
- one entry that scores exactly `PROMOTION_THRESHOLD` and promotes;
- one entry that scores just below `PROMOTION_THRESHOLD` and discards
  (these two together protect the boundary that a scoring bug is most
  likely to move);
- at least one entry captured from a real CHG-004 acceptance run so the
  corpus is grounded in observed evidence rather than only constructed
  cases.

Fixtures are treated as immutable evidence. Editing a fixture requires a
spec change with rationale; the check would otherwise be self-referential.

## Security invariants at the Layer-3 boundary

Once the loop runs a live model against real Java code, the model's output
becomes code that will execute inside a JVM the operator trusts. That is a
different trust posture from evolving a workflow file, and the spec pins
four invariants on it, each with an acceptance test:

1. **The candidate's environment does not carry the model-provider
   credential.** `T9b` scrubs the child environment of behaviour case
   scripts to an allow-list (`PATH`, `HOME`, `LANG`, `LC_*`, `JAVA_HOME`,
   plus operator-added variables). `SAAA_MODEL_API_KEY` is never present.
   `S11` proves the scrub with a candidate that deliberately tries to read
   it and observes the empty string.
2. **The MCP tool response never leaks the credential.** `T6b` adds a
   scrubber that replaces any occurrence of the current key value (and
   known transport headers) with `<redacted>` before the response reaches
   stdio. Defence in depth against a serialisation library, LangChain4j
   error trace, or downstream adapter that echoes a header. `S12` covers
   it.
3. **A CLI failure invoked in-process does not terminate the MCP server.**
   `T4` configures picocli with an execution exception handler and exit
   code mapper so a non-zero return does not become a `System.exit`.
   `S13` covers it with a companion test that drives a CLI failure through
   MCP and asserts the server still accepts the next request.
4. **Promotion cannot become a merge at compile time.** `T9c` narrows the
   promotion sink port so it exposes only "record a candidate ref". No
   overload has a target-branch parameter that could name `main`, and a
   source-scanning fitness function rejects a build in which the
   deterministic layer references `git merge`. `S14` covers both. This
   closes the "no code path merges" invariant at compile time rather than
   by review.

None of this sandboxes untrusted Java. A candidate can still open a socket,
write outside the worktree, or exhaust memory. Full sandboxing is a named
non-goal for this slice; the reasoning is that it is a much larger
investment than the vertical demonstration is worth. The credential-leak
vector is closed because it is the one that would be silent, hard to
reverse, and pointed at a widely-issued secret.

### Why not elitism yet

Elitism (never let population quality drop below the best-so-far) protects
against decay *across generations* in a population. This slice is still
single-candidate, single-generation, so there is no meaningful incumbent to
compare against. Elitism lands with the population foundation slice.

### Why not mutation testing (PIT) yet

Mutation testing grades the strength of a test suite by mutating production
code and asserting tests catch it. It is complementary to property-based
tests and a good next investment, but it is a separate tooling initiative
and is not needed for the guardrail floor this slice defines. Named as a
follow-on.

### Why not an independent-judge scorer yet

An "outer scorer" measuring the inner scorer's quality is the most powerful
form of guardrail — the mutated thing never grades its own promotion. Doing
it well requires a golden corpus large enough to distinguish "same
behaviour" from "same behaviour on this specific input set", which this
slice bootstraps but does not finish. Landed with a later scorer-as-target
slice.

## Considerations, alternatives, rejected shapes

### Provider adapter

**Chosen: OpenAI-compatible endpoint, `baseUrl` configurable.**
One adapter covers OpenAI, NeuralWatt, groq, together.ai, Ollama and any
future OpenAI-shaped provider. Fits the LangChain4j-adapter boundary
(`ARCH-001`) without proliferating provider-specific code.

Rejected: **per-provider adapters** (`OpenAiMutationProposer`,
`AnthropicMutationProposer`, `OllamaMutationProposer`). Duplicates wiring;
provider-specific code inside SAAA for no user benefit; a NeuralWatt-shaped
provider still needed base-URL configurability anyway.

Rejected: **local model only** (Ollama or in-process). Cheapest for CI but
does not demonstrate the loop against the model shapes real users care
about; and the OpenAI-compatible path already covers Ollama for free.

### Prompt shape

Not designed in this document. First cut is minimal: baseline definition
plus operator bounds plus a stop-word list, no exemplars. The proposer's
role is to *propose*, not to reason about scoring; anything more elaborate
belongs in a later slice once we see how noisy the first live runs are.

### L2 surface

**Chosen: MCP over stdio, wrapping the existing CLI.**
Small effort; matches ADR-0002's Layer-2 story; usable from Claude Code and
any MCP-capable outer agent immediately.

Rejected: **Java or Python SDK**. Same scorer output, different transport;
less useful when outer agents live in other runtimes; loses the
serialised-contract discipline the MCP option imposes.

Rejected: **CLI only, defer L2**. Contradicts the ADR delivery pattern for
the first vertical slice; and postponing L2 exposure would also postpone
the structured scorer-output contract that stresses the audit-trail
invariant on the wire.

### L3 target

**Chosen: `AuthorityLanguage.java`.**
Small; well-tested via `BoundedMutationValidator`; not part of the scoring
path; regression is bounded to a narrow domain. Safe first-slice target
under the staged-hybrid guardrails.

Rejected: **the scorer itself** (`PhenotypeFitnessScorer`). Load-bearing;
scoring a change to the scorer is the recursive-decay case the staged
hybrid explicitly defers. Cannot happen safely until the guards this slice
adds have real evidence of holding.

Rejected: **a scoring policy record** (`MutationOperatorDefaults`).
Bumps into the closed operator enum contract (`CON-001`); either good
guardrail practice or an unwanted distraction; not what "small first
target" means.

### Design strategy for the staged hybrid

State the staged hybrid explicitly rather than let it live only in the
conversation history:

> `ADR-0002` says the deciding step moves from reasoning into fixed code and
> the model may propose or repair but never approve. When the mutation target
> is the scorer itself, that boundary becomes recursive: the scorer grades a
> change to its own logic. Classical evolutionary computing has well-known
> guards for this — elitism, held-out validation, property tests, golden
> corpora, mutation testing, independent judges. SAAA adopts them in stages:
>
> 1. **Now (this slice).** Property tests + golden corpus. Encode intent, not
>    cases; check reproduced verdicts on frozen evidence. Enough to catch the
>    common decay modes even without a scorer-as-target slice, and immediately
>    useful as ordinary regression protection.
> 2. **With the population slice.** Elitism against the best-so-far scorer
>    output on the golden corpus.
> 3. **Before any scorer-as-target slice.** Mutation testing to grade the
>    test suite; independent-judge scorer for the promotion decision. Only
>    then is Option B (mutating the scorer) allowed.

The prohibition lives in three places so a later agent cannot quietly
enable scorer targeting by editing configuration alone:

- a non-goal in this spec's `change.toon`;
- a durable statement in this design document;
- a revisit trigger to be added to `ADR-0002` in a follow-up ADR revision
  ("any attempt to make `PhenotypeFitnessScorer` a mutation target requires
  a superseding ADR that names the guardrails proven to be in place"). That
  revision is not part of this change to avoid two ADR PRs racing; it is
  named in the handoff `next` list.

## What is deliberately out of scope

Named as non-goals in `change.toon` and repeated here for emphasis:

- population and crossover — foundation slices, not vertical;
- scorer-as-target — deferred by the staged hybrid;
- AST-aware realisation — whole-file text replacement still holds;
- mutation-testing sweep (PIT) — deferred to the next guardrail slice;
- retry policies for transient endpoint failures — operator concern;
- an outer agentic loop of our own — SAAA exposes; callers compose;
- promoted refs to `refs/heads/promoted/*` — a candidate branch pointer is
  enough for this slice;
- multiple L3 target files per run;
- any code path that could auto-merge a Layer-3 candidate under any score;
- MCP discovery / listing tools — the outer agent knows behaviour case
  names and target paths out-of-band for this slice; discovery is a
  follow-on;
- MCP tool arguments carrying credentials — credentials arrive only via
  the MCP server's environment;
- subprocess-per-invocation MCP transport — the MCP server invokes the
  CLI's picocli class in-process;
- providers requiring auth mechanisms beyond Bearer or API shapes beyond
  OpenAI Chat Completions — a subsequent adapter would handle those.

## Testing plan

Fidelities picked by risk. Real dependencies where cheap and material.

| Layer | Covers |
|---|---|
| unit | proposer output shaping, MCP schema, MCP response ordering, property tests, golden corpus |
| integration | LangChain4j against a recorded OpenAI-compatible endpoint (VCR-style or a local fake); `CommandCheckRunner` treating compiler failure as a failed check |
| acceptance | CLI run with `--profile openai-compatible` against a recorded endpoint; CLI run evolving `AuthorityLanguage`; MCP invocation returns structured `FitnessResult` and agrees with `journal.md` on commit sha |

Live endpoint calls are not required for CI. A recorded-endpoint fixture is
sufficient to prove the wiring; the operator can point at a real endpoint
for exploratory runs.

## Sizing and staging

This is a big slice by design — vertical across all three layers plus two
guards. Sensible staging within the slice, from most-to-least
prerequisite:

1. T9 (compiler failure as failed check) — general robustness fix that also
   unblocks L3.
2. T7 + T8 (property tests + golden corpus) — the guards, low-risk, land
   independently and can run in parallel with T9.
3. T1 + T2 + T3 (live proposer + profile registration) — L1 live. T2 may
   iterate on prompt shape more than expected (see risks); budget for it.
4. T4 + T5 + T6 (MCP module + schema + input hardening) — L2 live.
5. T10 + T10b (L3 acceptance test evolving `AuthorityLanguage` plus the
   journal-pollution gitignore + assertion) — the vertical demonstration.
6. T11 + T12 (docs + profile/handoff updates, Q-001 flip) — landing.

Steps can land as multiple PRs against `spec/chg-004-*` if the review appetite
is one-PR-per-concern rather than one-PR-per-spec.

## Revisit triggers

- Live proposer runs are so noisy that every candidate discards for the same
  parsing reason → the prompt shape needs its own slice before the population
  slice.
- MCP schema needs breaking changes to match what real outer agents want →
  version the schema now rather than let callers pin to a moving target.
- The golden corpus grows past what a human can reasonably review → invest
  in the independent-judge scorer earlier than planned.
- `AuthorityLanguage` turns out to be too easy a target (every mutation is
  either trivially wrong or trivially right) → replace with a slightly
  larger target in a follow-up slice before scorer-as-target is
  contemplated.

## Review history

This spec went through a lead self-review after the initial draft. The
findings that landed as spec-level tightening rather than as implementation
notes:

- MCP module placement moved from `modules/mcp` to a package inside
  `modules/adapters`, restoring the `invariant_named_module_layout`
  invariant.
- `S2` split into `S2` (endpoint unreachable) and `S2b` (API key rejected,
  with an explicit no-echo assertion) so the "does not leak the key"
  invariant has a dedicated test.
- `S9` added for the auto-merge prohibition, which was previously carried
  only by an "and" bullet inside `S5` and a non-goal.
- `S10` added for the `journal.md` pollution risk when the target folder
  is inside the SAAA repository itself; paired with `T10b` (`.gitignore`
  update plus assertion).
- MCP response shape gained `aggregateScoreDisplay` alongside the raw
  `aggregateScore`, and `journalPath` was clarified as absolute.
- Golden-verdict corpus format pinned to TOON per the repository's
  structured-data rule.
- MCP invocation of the CLI pinned to in-process rather than subprocess.
- Provider adapter scope narrowed to "endpoints that accept Bearer auth
  and the OpenAI Chat Completions API shape", with providers outside that
  scope declared out-of-slice.
- The "AuthorityLanguage may be too easy" mitigation replaced with a
  concrete constraint on the L3 acceptance test.
- The prior claim that the scorer-as-target prohibition was "repeated in
  the ADR's revisit triggers" removed; the follow-on ADR revision named
  in the handoff instead.

Second-pass finds that were addressed:

- `T9` reworded to reflect independent value, not only L3 unblocking.
- `T12` extended to flip `Q-001` from `open` to `answered-by-CHG-004`.
- New non-goals for MCP discovery tooling, credential-in-arguments, and
  subprocess transport.

A third pass split the review across two personas — an adversarial
security reviewer and an evolutionary-computing researcher — with these
outcomes:

*Security reviewer.*
- `S11` and `T9b`: a candidate that runs during L3 evaluation now executes
  in a scrubbed environment; `SAAA_MODEL_API_KEY` is not readable from
  the child process. A positive test proves the scrub.
- `S12` and `T6b`: the MCP response pipeline scrubs any occurrence of the
  current API key value before it reaches stdio. Defence in depth against
  a serialisation library or provider adapter echoing a header.
- `S13`: picocli invocation from the MCP server is configured with an
  execution exception handler so a CLI failure does not `System.exit`
  the MCP process.
- `S14` and `T9c`: the promotion sink port is narrowed at the type level
  so it cannot express a merge; a source-scanning fitness function
  rejects a build that references `git merge` from the deterministic
  layer. Closes the "no code path merges" invariant at compile time
  rather than by review.
- `T5` gained per-field and total response-size caps so a hostile
  candidate cannot DoS the stdio transport with oversized compiler
  output.
- Named non-goal added for full sandboxing of executed candidate Java;
  the environment scrub closes the credential-leak vector without
  claiming the JVM is sandboxed otherwise.

*Evolutionary-computing researcher.*
- Four new properties on `PhenotypeFitnessScorer`: parsimony monotonic
  in realization size, task_success monotonic in passing case count,
  decision matches the raw sum at the threshold boundary, and decision
  is derived from the raw sum rather than the rounded aggregate. The
  first two would catch a scorer mutation that inverts the sign or
  drops a component; the second two protect the PROMOTE/DISCARD
  boundary from silent shifts caused by rounding.
- Golden-corpus floor extended: one entry at exactly
  `PROMOTION_THRESHOLD` (PROMOTE), one just below (DISCARD), and one
  captured from a real CHG-004 acceptance run so the corpus is
  grounded in observed evidence and not only in constructed cases.
- `T2` clarified: `maxLinesChanged` is a bound on the *diff*, not on
  emitted content length. A same-length whole-file replacement that
  changes every line still exceeds the bound.
- `T2b` added: the exact prompt and raw model response are logged into
  the candidate's `.saaa/candidates/<id>.toon`. A later scorer-as-target
  audit can distinguish "the scorer changed its mind" from "the model
  produced a different proposal" only if the prompt is recoverable
  from the commit.
- Named non-goal added for proposer diversity knobs (temperature,
  top-p). They land with the population slice that actually needs
  them.
