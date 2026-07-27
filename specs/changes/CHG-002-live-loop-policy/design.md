# Live Mutation Loop Policy Design

## Vocabulary

The proposal uses genetic-algorithm vocabulary explicitly:

| Term | Meaning in this project |
|---|---|
| genotype | the implementation/workflow representation being evolved |
| individual | one versioned workflow candidate |
| mutation | a bounded behavioral variation proposed for an individual |
| operator | the mutation kind, such as reroute, insert guard, change model policy, adjust retry policy or alter tool-use strategy |
| loci | the workflow node, edge, policy or configuration points the mutation may affect |
| realization | the concrete Git diff used to materialize the mutation in a candidate worktree |
| phenotype | observed workflow behavior against evaluation cases |
| fitness | deterministic score calculated from phenotype evidence |
| selection | deterministic promote or discard decision derived from fitness policy |

`Mutation.patch` in the current Java record should be treated as a transitional
realization payload. The next implementation slice should replace it with a
typed mutation contract or a realization reference, because the domain mutation
is the behavioral variation, not the edit mechanics.

## Architecture

The next slice remains a local Java CLI over existing ports:

```text
cli
  -> application loop
       -> MutationProposer port
       -> MutationValidator port
       -> CandidateWorkspace port
       -> CheckRunner and BenchmarkRunner ports
       -> FitnessScorer port
       -> DecisionSink and metadata ports

adapters/langchain4j
  -> constructs provider-specific ChatModel
  -> exposes MutationProposer only
```

The core and application modules do not import LangChain4j, OpenAI SDK types,
SQLite, JMH, picocli or Git libraries.

## Provider Policy

The first live provider is OpenAI via LangChain4j's official OpenAI integration.
The CLI should fail clearly when live proposal mode is requested without:

- `SAAA_MODEL_PROVIDER=openai`
- `SAAA_MODEL_NAME`
- `OPENAI_API_KEY`

`OPENAI_BASE_URL` is optional and adapter-local. The model name remains
configuration, not a hard-coded latest-model decision.

Rejected for now:

- multiple providers in the first live slice
- provider-specific branching in core or application
- defaulting to a live network call when provider config is incomplete

## Mutation Contract Policy

The first mutation contract has two layers:

```text
TOON envelope -> canonical S-expression mutation IR -> candidate Git diff
```

TOON fits review and audit trails because the repository already uses it for
profile, handoff and structured specs. S-expressions fit the internal
implementation representation because mutation and crossover are tree
operations: operator, target, loci, bounds and gates can be canonicalized and
compared without prose parsing.

The internal S-expression IR uses a closed initial mutation operator enum:

| Operator | Default emphasis |
|---|---|
| `targeted-behavior-change` | bounded behavior change at one named locus |
| `repair` | reproduce failing behavior, fix it and preserve regression evidence |
| `simplify` | preserve behavior while reducing complexity or coupling |
| `performance-tune` | improve latency, throughput, allocation or cost without weakening behavior |
| `guardrail-change` | strengthen validation, safety or authority boundaries |
| `tool-strategy-change` | change agent tool selection, order or arguments |
| `model-routing-change` | alter model, provider or prompt routing policy |
| `prompt-policy-change` | alter prompt or policy text and evaluate scenario behavior |
| `hill-climb` | locally improve a known-good parent along one fitness dimension |
| `exploratory-leap` | test a higher-variance moonshot with explicit risk and evidence budgets |

Unknown operators are invalid until a future proposal adds their deterministic
bounds and evidence requirements.

Example targeted mutation contract:

```toon
mutation:
  id: MUT-001
  operator: targeted-behavior-change
  hypothesis: changing interest calculation to round only at money boundaries improves numerical stability without changing public API
  target:
    kind: method
    file: src/main/java/example/billing/InterestCalculator.java
    symbol: calculateInterest
  loci:
    - method_body
    - adjacent_unit_tests
  bounds:
    max_files_changed: 2
    max_lines_changed: 80
    public_api_change: false
    persistence_change: false
    production_config_change: false
  required_evidence:
    - unit_tests_pass
    - property_tests_pass
    - benchmark_not_worse_than_baseline
  fitness:
    hard_gates:
      - deterministic_checks_pass
      - required_evidence_present
    objectives:
      - id: task_success
        weight: 0.40
      - id: reliability
        weight: 0.20
      - id: cost_latency_budget
        weight: 0.20
      - id: behavioral_safety
        weight: 0.10
      - id: parsimony
        weight: 0.10
```

Internal S-expression mutation IR:

```lisp
(mutation
  (id MUT-001)
  (operator targeted-behavior-change)
  (target (kind method) (symbol calculateInterest))
  (loci method-body adjacent-unit-tests)
  (bounds
    (max-files-changed 2)
    (public-api-change false)
    (persistence-change false))
  (fitness
    (gate deterministic-checks-pass)
    (gate required-evidence-present)
    (objective task-success 0.40)
    (objective reliability 0.20)
    (objective cost-latency-budget 0.20)
    (objective behavioral-safety 0.10)
    (objective parsimony 0.10)))
```

The model may write implementation changes in the candidate worktree. The Git
diff and commit are the realization record. Validation must check the contract,
the diff and the resulting behavior.

## Crossover Policy

Crossover is useful later, but the first form should be conceptual:

```text
evaluated parent A -> useful trait + evidence
evaluated parent B -> useful trait + evidence
                      |
                      v
             child mutation contract
             using the closed mutation operator enum
                      |
                      v
             targeted implementation
```

Allowed first crossover:

- combines techniques, hypotheses, constraints or reviewer insights
- uses evaluated parents with recorded fitness evidence
- creates one new bounded mutation contract
- produces one canonical child S-expression mutation IR
- keeps one primary target locus

Deferred crossover:

- raw Git diff merging
- multi-locus recombination
- model-selected parents without deterministic evidence filters
- treating diversity-of-thought actor output as approval
- `conceptual-crossover` as a mutation operator enum value

This keeps crossover close to "combine lessons from two lab notes" rather than
"splice two code changes and hope."

## Loop Engineering

The evolutionary process is loop engineering. Each accepted contract changes or
tests the behavior of an agentic loop: planning, model routing, prompt policy,
tool use, validation, memory retrieval, checks or scoring. The operator enum is
the semi-declarative input that tells the next loop which default evidence and
fitness dimensions apply before any candidate realization is created.

## Search Posture Operators

Two operators shape how the loop balances exploitation and exploration:

- `hill-climb` is fitness-aware exploitation. It starts from an evaluated
  parent, selects one objective to improve and proposes a nearby bounded
  variant. It must record parent score, objective focus and expected delta.
- `exploratory-leap` is fitness-aware exploration. It may introduce a more
  ambitious technique, but only with explicit exploration budget, rollback-safe
  bounds and the same deterministic selection gate as every other candidate.

These operators are useful because LLM-generated implementations are
nondeterministic. The operator tells the loop whether to keep the model close to
the parent or invite a larger technique change; the fitness function still
decides.

## Promotion Policy

Candidate creation keeps using isolated Git worktrees and committed candidate
branches. Promotion means:

- record a deterministic promotion decision in SQLite
- create or update `refs/heads/promoted/<candidate-id>` to the candidate commit
- keep the original candidate branch and evidence addressable until retention
  policy exists

Promotion does not mean:

- merge to `main`
- force-push
- deploy
- delete discarded evidence

Rollback for the first slice is metadata reversal plus deleting or moving the
promotion ref in a later explicit command. Automatic rollback is deferred until
promotion has human-approved semantics.

## Fitness Policy

Scoring is deterministic and evidence-only. It evaluates the candidate
phenotype, not the realization payload.

Hard gates:

- mutation validation passed before candidate creation
- all required deterministic behavior cases passed
- candidate commit exists
- all deterministic checks passed
- required benchmark evidence exists and is parseable
- required reviewer evidence is present when configured as required

Initial objectives:

| Objective | Weight | Calculation |
|---|---:|---|
| task_success | 0.40 | graded task-quality improvement after required behavior cases have already passed |
| reliability | 0.20 | repeated runs stay within allowed variance and failure limits |
| cost_latency_budget | 0.20 | benchmark, latency and cost measurements satisfy configured budgets |
| behavioral_safety | 0.10 | safety, authority and tool-use constraints are respected |
| parsimony | 0.10 | bounded function of mutation scope, touched loci and realization size |

Initial threshold:

- promote when hard gates pass and aggregate score is at least `0.80`
- discard otherwise

Auditability is a hard gate, not a weighted objective.

Benchmark budgets should be absolute thresholds first. Relative baseline
comparison is deferred until the runner stores directly comparable baseline and
candidate benchmark evidence from the same invocation.

## Actor Review Policy

Reviewer actors are not approvers. They produce bounded review evidence that
the deterministic policy may require, ignore or treat as advisory by
configuration.

When actor tooling is unavailable:

- record `review_unavailable` evidence
- do not treat the review as passed
- if reviewer evidence is required, stop before promotion and require human
  review or policy change
- if reviewer evidence is advisory, continue with deterministic checks and
  record the lost independent challenge

This mirrors the repository integration fallback while keeping the runtime loop
auditable under missing tools.

## Nondeterminism Policy

LLM implementation is nondeterministic. The loop should record enough evidence
to replay the experiment boundary even if the exact model sample cannot be
recreated:

- model provider, model name and model parameters
- prompt or typed-service input
- mutation contract
- candidate branch, commit and diff summary
- deterministic checks, benchmark runs and reviewer evidence
- attempt number and stop reason

The first implementation evaluates one candidate per accepted mutation
contract. When comparing stochastic operators or model settings, use repeated
runs and compare distributions, not a single lucky candidate.

## Revisit Conditions

Revisit these decisions when:

- TOON contracts become too ambiguous for deterministic validation
- S-expression mutation IR grows large enough to require a dedicated parser or
  schema validator
- workflow definitions need type-specific validation beyond method/file/symbol
  targeting
- benchmark throughput or variance prevents stable local scoring
- users require promoted candidates to target protected integration branches
- reviewer actors become a required governance gate rather than advisory
  experiment evidence
- retrieval corpus size justifies adding LangChain4j retrieval integration
  beyond repo-native context
