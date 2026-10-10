# CHG-029: A live proposer fills a generation, and every candidate's AgentUsage is recorded

## Why

Two gaps, one slice.

`ADR-0002` names the first under Layer 1, "what's missing": a live model
proposer filling a generation (`docs/decisions/0002-three-layer-vision.md`,
Layer 1). Today every generation's variety comes from the fixture proposer
padding its own canned mutation, so the spread a generation records measures
padding volume and nothing about candidate quality — CHG-026 says so in its own
risks and independent review confirmed the claim is honest.

`ADR-0006` names the second as its slice 0
(`docs/decisions/0006-budget-aware-harness-optimisation.md`, "Delivery
sequence"): cost per successful task cannot be measured, because `AgentUsage`
is returned by the harness adapters but written to neither the journal nor the
experiment ledger, and because no live proposer fills a generation, so there is
no real cost to measure. Nothing in ADR-0006's slices A to F can be evaluated
until this exists.

## Intent

Run a generation whose candidates come from the live `openai-compatible`
proposer rather than the fixture, and record each candidate's observed agent
usage in the journal and the experiment ledger, with unobserved fields recorded
as unavailable — never as zero.

## What exists today

Everything below is verified against the code, not remembered.

**The live proposer exists and is one profile.** `--profile openai-compatible`
resolves through `ProposerProfileRegistry.resolve`
(`modules/adapters/src/main/java/com/dreamthought/saaa/adapters/evolve/ProposerProfileRegistry.java:39`)
to `OpenAiCompatibleMutationProposerFactory.fromApplicationConfig()`
(`modules/adapters/src/main/java/com/dreamthought/saaa/adapters/langchain4j/OpenAiCompatibleMutationProposerFactory.java:17-19`),
which builds a `LangChain4jMutationProposalAdapter` that implements
`MutationProposer` directly
(`modules/adapters/src/main/java/com/dreamthought/saaa/adapters/langchain4j/LangChain4jMutationProposalAdapter.java:34`).
It is covered by a WireMock-backed acceptance test,
`EvolveCommandAcceptanceTest.recordsLiveProposerPromptDigestAndRawResponseInCandidateBookkeeping`
(`modules/cli/src/acceptanceTest/java/com/dreamthought/saaa/cli/EvolveCommandAcceptanceTest.java:313-359`),
whose stub answers one canned mutation for one candidate
(`EvolveCommandAcceptanceTest.java:574-603`).

**It cannot fill a generation.** The adapter overrides the two single-argument
entry points (`LangChain4jMutationProposalAdapter.java:67-79`) and not the
variant entry point, whose default ignores the variant
(`modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/MutationProposer.java:29-34`).
`Q-013` records the same for `AgentHarnessMutationProposer` and notes the
variant number never reaches the model prompt. The only proposer that honours
the variant is the fixture
(`modules/adapters/src/main/java/com/dreamthought/saaa/adapters/fixture/FixtureMutationProposer.java:72`).
So a live generation asks the model the identical question N times; a model
that answers with the same id twice dies at candidate two under the
duplicate-mutation rule (`GenerationEvaluationLoop.java:100-112`), after paying
for two evaluations, leaving two fitness rows and no generation row.

**The generation wiring itself is proposer-neutral.** `EvolveRunner` builds one
`MutationEvaluationLoop` per candidate position and forwards the variant
through `VariantProposer` (`EvolveRunner.java:224-235` and
`EvolveRunner.java:385-408`); `--candidates N` and `--profile` already coexist
on the command line (`EvolveCommandAcceptanceTest.java:329-334` and
`EvolveCommandAcceptanceTest.java:522-526`). Nothing between the CLI and the
adapter knows which proposer was resolved. One caveat, found in independent
review: `VariantProposer` forwards `proposerEvidence()` to its delegate
(`EvolveRunner.java:404-407`) but would not forward a *new* accessor unless
made to — a default-empty usage accessor plus a non-delegating wrapper would
silently record unavailable for every live candidate. T2 names the delegation
an explicit requirement and pins it with a test (S5).

**Usage is produced and dropped.** `AgentUsage` is a domain record of input
tokens, output tokens, credits, wall clock and retries, all primitive and all
mandatory
(`modules/domain/src/main/java/com/dreamthought/saaa/domain/AgentUsage.java:7-26`),
carried by `AgentRunResult.usage`
(`modules/domain/src/main/java/com/dreamthought/saaa/domain/AgentRunResult.java:13`).
Both harness adapters return it: `AcpAgentHarness` measures wall clock but
writes placeholder zeros for tokens and credits
(`modules/adapters/src/main/java/com/dreamthought/saaa/adapters/acp/AcpAgentHarness.java:72,83`),
and `MutationProposerAgentHarness` returns `AgentUsage.none()` on every outcome
(`modules/adapters/src/main/java/com/dreamthought/saaa/adapters/evolve/MutationProposerAgentHarness.java:32,43,52`).
`AgentHarnessMutationProposer.invoke` then keeps only the mutation and discards
the rest (`modules/adapters/src/main/java/com/dreamthought/saaa/adapters/evolve/AgentHarnessMutationProposer.java:85`).
On the openai-compatible path the token counts are observed —
`AuditingChatModel` captures `response.tokenUsage()`
(`LangChain4jMutationProposalAdapter.java:224-227`) — but survive only as
unparsed string attributes in the candidate bookkeeping file inside the
candidate worktree (`LangChain4jMutationProposalAdapter.java:107-112`). The
journal's per-candidate entry carries commit, checks, score and decision, no
usage (`modules/adapters/src/main/java/com/dreamthought/saaa/adapters/journal/JournalReporter.java:113-146`),
and the ledger's migrations 1 to 3 define no usage table or column
(`modules/adapters/src/main/java/com/dreamthought/saaa/adapters/sqlite/SqliteExperimentMetadataStore.java:179-313`).

**Two defects found in independent review, both verified against the code and
both closed by this change:**

- **A measured zero and a placeholder zero are indistinguishable.** Because
  `AgentUsage`'s fields are primitive (`AgentUsage.java:7-13`),
  `AcpAgentHarness`'s placeholder zeros (`AcpAgentHarness.java:72,83`) are
  numerically identical to an honestly measured zero, and no downstream
  translation can recover which was meant. Availability must be declared per
  field at the harness boundary — see "AgentUsage declares availability" below
  and S8.
- **The auditing model leaks tokens across invocations.** `AuditingChatModel`
  sets `inputTokens`/`outputTokens` only when `tokenUsage()` is non-null and
  never clears them (`LangChain4jMutationProposalAdapter.java:220-228`), while
  `proposerEvidence()` reads whatever was last captured
  (`LangChain4jMutationProposalAdapter.java:97-118`). So a response without a
  usage block inherits the *previous* call's counts — in a generation,
  candidate two would be recorded with candidate one's tokens. Today the leak
  reaches only the candidate bookkeeping file; once this slice persists usage,
  it would become durable audit data. The channel clears captured state at the
  start of every invocation (T3), and S6's two-variant test is the standing
  guard.

**The price identity has no path today.** `S5` records provider and model
beside usage because cost is comparable only between attempts whose price
identity is known (ADR-0006 slice 0). But the openai-compatible path carries no
route: `OpenAiCompatibleMutationProposerFactory` passes only the literal
`"openai-compatible"` as the proposer id
(`OpenAiCompatibleMutationProposerFactory.java:17-23`), and
`LangChain4jMutationProposalAdapter` holds no route or model. The identity is
available one step earlier: `ModelEndpointConfig` already carries `modelName`
(`modules/adapters/src/main/java/com/dreamthought/saaa/adapters/langchain4j/ModelEndpointConfig.java:3`),
which `OpenAiCompatibleChatModelFactory` passes into the model builder
(`OpenAiCompatibleChatModelFactory.java:21-27`). A requested model is
not proof of the model that answered, so the price identity is taken from what
the provider reports: LangChain4j's `ChatResponse` carries the model name the
response declares, and the adapter stamps that on the observation (T3). Where
the response declares none, the model is NULL. The configured model name is
threaded through the factory (T3) only so the provider column can say
`openai-compatible` and so a mismatch between requested and reported model is
visible in the journal; it is never written as the price identity.

The harness path does **not** get a price identity from its route. `AgentRoute`
documents itself as recording "intent, not provider proof"
(`AgentRoute.java:3`), and the ACP profile's route names its model as the literal
`"configured"` (`ProposerProfileRegistry.java:40-43`). Writing that as a price
identity would be exactly the placeholder this change exists to remove. Until an
ACP agent reports the model it actually used, the harness path records provider
and model as NULL, and the route stays where it already is, in the audit of what
was intended. A fixture candidate has no identity either, and is NULL too.

## Q-013, settled

`Q-013` asks: what identifies two candidates in a generation as the same
mutation? The duplicate rule compares mutation ids alone, which is exact for
the fixture proposer and blunt for a live one whose ids are model-chosen free
text. The question records three candidate answers:

1. **Compare `(id, patch)`.** Same id and same body is non-variation and keeps
   the hard failure; same id and different body is two mutations and ranks.
2. **Put the variant number in the prompt** and require distinct ids. Weaker,
   because it trusts the model to comply, and the check exists precisely
   because that trust is what must not be required.
3. **Derive identity from the realised tree.** Strongest, and the most work.

**Recommendation: adopt option 1, and adopt the asking half of option 2 as
production mechanism rather than as check.** The two halves answer different
questions: the prompt carries the variant so the model *can* vary (S1), and the
rule compares `(id, patch)` so compliance is verified rather than assumed
(S3/S4) — the same division of labour `MutationProposer.java:24-27` already
documents for the variant default. Option 1 is the smallest change that makes
the failure diagnosis exactly true, and Q-013 itself names it first in order of
preference.

**The surviving alternative: option 3 is the recorded destination, not this
slice.** Comparing the realised tree rather than the proposal is the same
instinct as the declared-locus gate — check the realisation, not the
declaration — and it is the only answer that survives a proposer that varies
its ids over identical patches. Q-013 is updated to answered with this
recommendation and that residue (T8).

**The mechanism constraint is real and named in Q-013:** the rule lives in
`GenerationEvaluationLoop`, which sees only `FitnessResult`
(`GenerationEvaluationLoop.java:63-64`), and `FitnessResult` carries the
`Candidate`, not the `Mutation`
(`modules/domain/src/main/java/com/dreamthought/saaa/domain/FitnessResult.java:6-16`).
So the per-candidate evaluation return widens to carry the `Mutation`
alongside the result (T6). The single-candidate
`MutationEvaluationLoop.evaluate` signature is preserved for its current
callers; `CandidateEvaluator` is shared by the single-candidate and generation
paths (`EvolveRunner.java:121-147`), so the existing single-candidate tests and
the CHG-026 acceptance suite are the regression net for the widening.

## AgentUsage declares availability at the boundary

The central domain decision, forced by the first review blocker: rather than
adding a second usage type and translating between it and `AgentUsage`,
**`AgentUsage` itself is redefined with per-field availability** (T1):

- each consumption field — input tokens, output tokens, credits, wall clock,
  retries — is optional; empty means *unobserved*, a present zero means a
  *measured zero* (a fully cached call that billed nothing is a real
  measurement), and present values reject negatives exactly as today
  (`AgentUsage.java:14-22`);
- it also carries an optional price identity — provider and model — for the
  reason S5 records;
- `AgentRunResult` keeps carrying it (`AgentRunResult.java:13`), so a harness
  declares availability when it constructs the result: `AcpAgentHarness`
  declares wall clock measured and the rest unavailable;
  `MutationProposerAgentHarness` declares everything unavailable, because it
  wraps a local proposer and nothing is invoked
  (`MutationProposerAgentHarness.java:16-18`);
- the placeholder-zero defect class is closed at construction, not by a
  translator downstream: there is no longer a shape in which "0" can mean "I
  didn't look".

The blast radius is contained and compile-checked: `AgentUsage` is constructed
only by the two harness adapters and tests (the grep-visible consumers are
`AgentRunResult`, `AcpAgentHarness`, `MutationProposerAgentHarness` and their
tests), and a record's components are checked everywhere at compile time, so
T1 cannot half-land. `AgentUsage.none()` survives as the
all-fields-unavailable spelling.

## Scenarios and acceptance mapping

Each scenario is given/when/then; the mapping follows the existing
`change.toon` acceptance format (`module/Class.method`, layer one of
acceptance / unit / integration).

**S1.** *Given* a run with `--profile openai-compatible` and `--candidates N`,
*when* the generation is evaluated, *then* the proposer is asked for variant k
explicitly and the variant marker is observable in the request to the model.

- `adapters/LangChain4jMutationProposalAdapterTest.theVariantNumberReachesTheModelPrompt`
  — unit
- `cli/EvolveCommandAcceptanceTest.aLiveProposerIsAskedForEachVariantExplicitly`
  — acceptance

**S2.** *Given* the same run, *when* the live proposer returns N distinct
variants, *then* the generation evaluates, ranks and records all N exactly as
the fixture-filled path does, through the existing per-candidate wiring.

- `cli/EvolveCommandAcceptanceTest.aLiveProposerFillsAGenerationAndTheRankingIsRecorded`
  — acceptance
- `adapters/EvolveRunnerTest.theVariantReachesTheProposerThroughThePerCandidateWiring`
  — unit

**S3.** *Given* a generation, *when* two candidates carry the same mutation id
and the same patch, *then* the run fails and the failure names non-variation as
the cause — now an exactly-true diagnosis rather than a half-true one.

- `deterministic/GenerationEvaluationLoopTest.theSameMutationIdOverTheSamePatchFailsNamingNonVariation`
  — unit
- `cli/EvolveCommandAcceptanceTest.aLiveGenerationWhoseProposerRepeatsIdAndPatchFailsNamingNonVariation`
  — acceptance

**S4.** *Given* a generation, *when* two candidates carry the same mutation id
over different patches, *then* both are evaluated and ranked, because a
model-chosen id is a hint and the patch is the substance.

- `deterministic/GenerationEvaluationLoopTest.theSameMutationIdOverDifferentPatchesRanks`
  — unit
- `cli/EvolveCommandAcceptanceTest.aLiveGenerationRanksCandidatesThatShareAnIdOverDifferentPatches`
  — acceptance

**S5.** *Given* a generation filled by the live proposer, *when* a candidate
finishes its evaluation, *then* the ledger holds a usage row keyed to that
candidate carrying the provider and the model the response declared, the token
counts the provider returned and the wall clock the adapter measured, with
credits NULL — and the model column equals the response-declared model, which
the stub makes different from the configured one so that copying the request
instead fails.

- `cli/EvolveCommandAcceptanceTest.aLiveGenerationRecordsEachCandidatesObservedUsage`
  — acceptance
- `deterministic/MutationEvaluationLoopReportingTest.aCandidatesObservedUsageReachesTheStoreAndTheReporters`
  — unit
- `adapters/SqliteExperimentMetadataStoreIntegrationTest.aCandidatesUsageIsRecordedWithPriceIdentityAndUnobservedFieldsNull`
  — integration
- `adapters/EvolveRunnerTest.thePerCandidateWrapperForwardsTheProposersUsageChannel`
  — unit (the wrapper-delegation pin: the fixture proposer reports a
  distinctive usage, and a `VariantProposer` that drops the accessor records
  unavailable and fails the test)

**S6.** *Given* a generation of two from the live proposer, *when* variant
one's response carries a distinctive usage block and variant two's response
omits it, *then* variant two's usage row exists with its token fields NULL,
variant one's row carries its own counts, and the run completes — an absent
observation is recorded, never inherited and never zero.

- `cli/EvolveCommandAcceptanceTest.aNoUsageResponseDoesNotInheritThePreviousCandidatesTokens`
  — acceptance
- `adapters/LangChain4jMutationProposalAdapterTest.aResponseWithoutUsageDoesNotInheritThePreviousCallsTokens`
  — unit

**S7.** *Given* a generation run with `--profile fixture`, *when* the
candidates are evaluated, *then* one `candidate_usage` row exists per candidate
with every field NULL — usage and price identity alike — because the fixture
profile constructs `FixtureMutationProposer` directly
(`ProposerProfileRegistry.java:37-38`), no model is ever invoked, and the
default-empty channel accessor is the only honest report.

- `cli/EvolveGenerationAcceptanceTest.aFixtureGenerationRecordsEveryCandidatesUsageAsUnavailable`
  — acceptance

**S8.** *Given* the harness path, *when* two invocations report equal raw
numbers with different availability — a measured zero against an unobserved
field — *then* the recorded observations differ: the measured zero persists as
zero and the unobserved field as unavailable. With primitive fields both would
record identically, which is the shape the domain redefinition removes.

- `adapters/AgentHarnessMutationProposerTest.equalRawValuesWithDifferentAvailabilityAreRecordedDifferently`
  — unit
- `domain/AgentUsageTest.aMeasuredZeroAndAnUnobservedFieldAreDifferentValues`
  — unit

**S9.** *Given* a candidate's journal entry is written, *when* a person reads
it, *then* it carries the observed usage fields and renders unobserved fields
as unavailable.

- `adapters/JournalReporterTest.aCandidateEntryCarriesItsObservedUsageAndRendersUnavailable`
  — unit

## Tasks

Thin, independently green, one reviewable idea each. Independent review found
the first draft's T3 and T5 too broad; both split cleanly, and the split is
recorded with each. T1 to T3 are the channel, T4 and T5 are the record, T6 is
Q-013, T7 is the live fill, T8 is knowledge.

- **T1** — `AgentUsage` redefined with per-field availability plus an optional
  price identity, as specified above; `AgentRunResult` keeps carrying it; both
  harness adapters declare availability at construction. Includes the
  equal-raw-values-different-availability domain test (S8, domain half).
  Compile-checked blast radius; lands green because every constructor is
  updated in the same commit.
- **T2** — the usage channel: a default-empty accessor on `MutationProposer`
  following the `proposerEvidence` precedent (`MutationProposer.java:36-38`),
  **and an explicit requirement that `EvolveRunner.VariantProposer` forwards
  it** exactly as it forwards `proposerEvidence()`
  (`EvolveRunner.java:404-407`), pinned by the wrapper-delegation test (S5)
  whose fixture reports a distinctive usage and fails if the wrapper drops the
  accessor. Green alone: the port and the wrapper change compile with the
  default, and the test pins the delegation before any adapter populates it.
- **T3** — the adapters populate the channel: `LangChain4jMutationProposalAdapter`
  reports tokens when the provider returns them, wall clock measured around
  the call, credits and retries unavailable, and the price identity whose model
  is threaded from `ModelEndpointConfig` (`ModelEndpointConfig.java:3`,
  `OpenAiCompatibleChatModelFactory.java:21-27`) through
  `OpenAiCompatibleMutationProposerFactory` in place of only the literal
  profile id (`OpenAiCompatibleMutationProposerFactory.java:17-23`).
  `AuditingChatModel` clears captured state at the start of every invocation,
  closing the stale-token leak (S6 unit). `AgentHarnessMutationProposer`
  forwards `AgentRunResult.usage` unchanged — availability is explicit at the
  source since T1, so there is no translation to get wrong (S8 adapter half).
- **T4** — the ledger record, standalone: migration 4 adding `candidate_usage`
  (provider, model, per-field NULL for unavailable, credits as exact decimal
  text), an abstract `recordUsage` on the `ExperimentMetadataStore` port
  following the `recordGeneration` precedent
  (`modules/deterministic/src/main/java/com/dreamthought/saaa/deterministic/ExperimentMetadataStore.java:12-18`),
  the SQLite implementation, and direct integration tests writing and reading
  rows (S5 integration). This is the first half of the first draft's T3; it is
  independently green precisely because nothing consumes the port yet — the
  store is tested directly.
- **T5** — the wiring: `MutationEvaluationLoop` reads the channel and writes
  usage beside the fitness row (`MutationEvaluationLoop.java:237`), the
  reporter callback, the journal's per-candidate usage line (S9), the
  loop-level store-and-reporter unit test (S5 unit) and the actual
  `--profile fixture` generation acceptance that pins one all-NULL row per
  candidate (S7). This is the second half of the first draft's T3; it is green
  on top of T1 to T4 and is the commit that makes the channel observable.
- **T6** — Q-013 settled in code: the per-candidate evaluation return carries
  the `Mutation`, `GenerationEvaluationLoop` compares `(id, patch)` (S3, S4,
  unit and WireMock acceptance). Independent of T2 to T5 — the rule change
  touches neither the channel nor the ledger — and may land before or after
  them.
- **T7** — the live fill: the adapter overrides the variant entry point and
  puts the variant into the model prompt (S1, S2), with WireMock acceptance for
  a filled generation, for usage recorded from per-variant stubs (S5
  acceptance) and for the two-variant stale-state guard (S6 acceptance). This
  was the first draft's T5; the Q-013 acceptance tests moved to T6 because they
  belong to the rule change, not the prompt change — a stub can return
  identical or same-id responses without the variant marker, so T6 needs
  nothing from T7 and the two halves stay independently green.
- **T8** — knowledge and documentation in the same change: `Q-013` answered
  with the recommendation and the surviving alternative, ADR-0006 slice 0 noted
  as delivered, HANDOFF, README and wiki updated where they name this gap.

## The schema change

One new table, added as migration 4 in `SqliteExperimentMetadataStore.migrate`
beside the existing migrations 1 to 3
(`SqliteExperimentMetadataStore.java:179-313`):

```sql
create table if not exists candidate_usage (
  candidate_id text primary key not null references candidates(id) on delete cascade,
  provider text,
  model text,
  input_tokens integer,
  output_tokens integer,
  credits_consumed text,
  wall_clock_millis integer,
  retries integer
)
```

The rules that make this honest rather than merely present:

- **Price identity is recorded beside the usage, and only when observed.**
  `provider` and `model` are the observation's price identity: on the live path,
  `openai-compatible` and the model name the provider's response declares; on the
  harness path NULL, because an `AgentRoute` records intent and the ACP route's
  model is the placeholder `"configured"`; NULL for a fixture candidate. They are
  never defaulted to a placeholder or copied from a request. ADR-0006 slice 0
  requires it: cost is only comparable between attempts whose price identity is
  known.
- **Every field is nullable, and NULL means unavailable — never zero.** A zero
  is a measurement claim: zero tokens billed, zero credits spent. A field the
  proposer did not observe is recorded as unavailable, per AGENTS.md ("say
  `unavailable` rather than estimating an unsupported price") and ADR-0006
  decision 4 ("Missing usage or price evidence is recorded as unavailable
  rather than invented"). The precedent is migration 3's nullable spread:
  inventing a zero would read as a finding rather than an absence
  (`SqliteExperimentMetadataStore.java:263-268`). The domain value makes the
  distinction before the schema is asked to: a present zero in `AgentUsage`
  persists as zero and an empty field as NULL, which is what S8 pins.
- **`credits_consumed` is exact decimal text**, not a SQLite real. Credits are
  money, and `RISK-007` is the recorded cost of letting a rounded
  representation become the value of record. For every proposer in this slice
  the field is NULL anyway: no chat-completions response reports a price, and
  none is estimated.
- **One row per evaluated candidate**, keyed by `candidate_id` with the same
  `on delete cascade` treatment the other per-candidate tables use
  (`SqliteExperimentMetadataStore.java:200`). A candidate whose proposer
  observed nothing still gets a row of NULLs: absence of a row is
  indistinguishable from a lost write, and "recorded as unavailable" should be
  a fact, not an interpretation of a gap.
- **No data migration.** The experiment ledger is derived data and is rebuilt,
  as with the CHG-023 and CHG-024 schema changes.
- **No generation-level aggregate.** Cost per successful task is ADR-0006
  slice F; totals are derivable from the per-candidate rows by a join until
  then.

## How determinism is kept

- **Nothing provider-aware enters `modules/deterministic`.** The redefined
  `AgentUsage` is a plain domain record of optionals. The port additions —
  `MutationProposer`'s usage accessor and `ExperimentMetadataStore.recordUsage`
  — carry that domain value and nothing else. The LangChain4j boundary fitness
  function continues to scan `modules/domain` and `modules/deterministic` for
  provider imports.
- **All measurement happens in adapters.** Token counts come from the provider
  response inside `AuditingChatModel`; wall clock is measured around the call
  in the adapter; availability is declared by the harness that did or did not
  observe each field. The deterministic layer transports and persists values it
  did not produce, exactly as it persists `BenchmarkEvidence` it did not
  measure.
- **The live proposer stays in adapters.** Profile resolution stays in
  `ProposerProfileRegistry` (`modules/adapters/.../evolve/ProposerProfileRegistry.java:21-60`),
  and LangChain4j stays inside `adapters/langchain4j`.
- **Tests never touch a real provider.** Acceptance tests use WireMock, as the
  existing live-proposer test does (`EvolveCommandAcceptanceTest.java:568-609`;
  WireMock 3.13.2, `gradle/libs.versions.toml:16`). Keying stubs on the
  variant marker needs only the request-body matching already in use
  (`EvolveCommandAcceptanceTest.java:578-579`). Deterministic-layer tests use
  fake proposers returning fixed usage values.
- **Recording is not approval.** Usage is evidence about what a candidate cost
  to propose; it plays no part in validation, scoring, ranking or promotion,
  and no routing consumes it — that is ADR-0006 slices A to C, excluded below.

## Non-goals

- harness profiles, deterministic profile selection and progressive escalation
  — ADR-0006 slices A to C, each its own change;
- any routing, learned or otherwise. `Q-010` stays open; this slice produces
  evidence it will consume;
- cost per successful task, Pareto comparison and any cross-candidate cost
  aggregation — ADR-0006 slice F;
- credit or price estimation of any kind;
- variant-aware prompting for the ACP harness proposer. Its usage forwarding
  lands here (S8); its generation-filling does not. `openai-compatible` is the
  one live proposer this slice fills;
- parallel candidate evaluation. The usage channel shares the
  `proposerEvidence` shape — per-candidate latest-proposal state
  (`LangChain4jMutationProposalAdapter.java:97-118`) — which is sound only
  because evaluation is sequential (`GenerationEvaluationLoop.java:14-18`);
- usage of a proposal whose candidate was never created (see open questions);
- migration of pre-change ledger rows; the ledger is derived and rebuilt;
- trace or corpus ingestion — ADR-0006 slice E.

## Risks, and where an assertion could pass for the wrong reason

The PAT-004 shapes this change is most exposed to, and the construction that
disarms each:

| Scenario | Trap | How the test avoids it |
|---|---|---|
| S1 | The stub answering any request would let "asks for variant k" pass against a proposer that never sends it | The WireMock stub matches the request body for the variant marker; a proposer that omits it gets no answer and the run fails |
| S2 | A generation could look filled while the variant never reached the proposer | The stub's per-variant answers are reachable only through the marker, so N distinct candidates exist only if the marker did |
| S3 | Any non-zero exit — unmatched stub, validation rejection — would satisfy a bare "run fails" assertion | The assertion pins the reason: the message must name the repeated id and that the patch matched, not only that the run failed |
| S4 | Exit zero would pass even if the second candidate had been quietly dropped | The test asserts two ranking rows exist in the ledger for the two distinct candidates, not only that the run succeeded |
| S5 | A key written whatever the outcome: asserting a usage row exists, or any token count, passes against a constant or zero write — and a usage accessor dropped by the per-candidate wrapper would record unavailable for every live candidate while every store-level test stays green | Per-variant stubs return distinctive token counts and each candidate's row is asserted equal to its own variant's values; the model column is asserted equal to the configured model name rather than non-null; the wrapper-delegation test pins the channel through `VariantProposer` with a fixture whose usage cannot be mistaken for the default |
| S6 | A single-candidate "absent usage" test cannot fail for the stale-state defect, because an empty store has nothing to inherit from | Two variants: variant one carries distinctive usage, variant two none. Variant two's NULLs prove the absence was recorded; variant one's asserted values prove the bleed had something to be caught against |
| S7 | A store persisting `AgentUsage.none()` zeros, or inventing a placeholder provider, looks identical to a correct write unless NULL is asserted — and the fixture profile never touches a harness adapter, so a harness-level test would not cover it | The test runs an actual `--profile fixture` generation (`ProposerProfileRegistry.java:37-38` constructs the proposer directly) and asserts one row per candidate with every field NULL |
| S8 | With primitive fields, a measured zero and an unobserved field record identically; any test on that shape is vacuous for the distinction that matters | The test uses equal raw values with different availability and asserts the records differ; the domain redefinition makes the distinction expressible at construction |
| S9 | A positive-wall-clock assertion would be flaky, not wrong: a fast stub can complete inside zero milliseconds | Wall clock is asserted present, never asserted positive |

Further risks:

- **The residual trust after Q-013's settlement.** A model that varies its ids
  over identical patches now ranks N identical candidates. That is exactly the
  case option 3 (realised-tree identity) closes, recorded as the destination in
  Q-013 rather than built here — the same scoping CHG-026 used to keep a
  ranking defect and a proposer defect from arriving together.
- **The channel is order-dependent.** The usage accessor is per-candidate
  latest-proposal state, as `proposerEvidence()` already is. It is sound under
  sequential evaluation only; the parallelism non-goal exists so the assumption
  cannot erode unnoticed, and any future parallel slice must move usage onto
  the returned value. The `AuditingChatModel` stale-state defect is what this
  shape does when done carelessly; the per-invocation clearing in T3 and S6's
  two-variant test are the standing guard.
- **The widening touches a shared port.** `CandidateEvaluator` serves both the
  single-candidate and generation paths (`EvolveRunner.java:121-147`). The
  widening preserves the `MutationEvaluationLoop.evaluate` signature its
  current callers use, and the CHG-026 acceptance suite plus the existing
  single-candidate tests are the regression net.
- **Redefining `AgentUsage` is a compile-checked blast radius.** Every harness
  adapter construction updates in T1's commit; that is intended, and it is what
  closes the placeholder-zero class rather than translating around it.
- **Cost realism.** A live-filled generation costs real tokens per candidate in
  production use. In tests it costs none: no test may require a real provider.

## Open questions

- **Retries visibility.** Whether the LangChain4j client retries internally is
  not determinable from this repository. `AuditingChatModel` sees one `doChat`
  per proposal (`LangChain4jMutationProposalAdapter.java:220-228`), so the
  adapter cannot claim zero retries happened inside the client. Until this is
  known, `retries` is recorded as unavailable from the openai-compatible
  adapter rather than as zero.
- **The cost of a proposal whose candidate was never created.** A mutation that
  fails validation after the model answered consumed tokens but has no
  candidate id (`MutationEvaluationLoop.java:209-217` throws before candidate
  creation at `:219-224`), and the generation records the attempt only as
  unevaluated (`GenerationEvaluationLoop.java:69-70`). Slice 0 records usage
  per evaluated candidate; whether that consumed-but-candidate-less usage
  belongs on the generation's unevaluated list needs its own decision, and it
  matters to the cost-per-successful-task unit ADR-0006 wants.
- **Provider coverage of `tokenUsage()`.** Whether every OpenAI-compatible
  endpoint populates the usage block is not knowable from the stub; S6 exists
  so the absent case is the tested case rather than the surprising one.

## Related knowledge

`ADR-0002` (the Layer-1 gap), `ADR-0006` (slice 0 and the unavailable-not-zero
rule), `ARCH-001` (the deterministic boundary), `CON-002` (the audit record),
`Q-013` (settled here), `PAT-004` (the assertion shapes above), `RISK-007`
(why credits are exact text).
